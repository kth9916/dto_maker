package com.maker.action;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.LightVirtualFile;
import com.maker.state.MappingPluginState;

/**
 * IntelliJ IDEA 플러그인 액션: 로드된 소스 클래스와 락온된 대상 클래스 정보를 바탕으로
 * Builder 패턴 변환 코드를 생성하고 사용자에게 보여줍니다.
 */
public class GenerateMappingCodeAction extends AnAction {

	public GenerateMappingCodeAction() {
		super("Shot!!"); // 버튼/메뉴에 표시될 이름
	}

	/**
	 * update 메소드가 실행될 스레드를 지정합니다.
	 * 현재 로직은 EDT에서 실행해도 괜찮습니다.
	 */
	@Override
	public @NotNull ActionUpdateThread getActionUpdateThread() {
		return ActionUpdateThread.EDT; // <-- EDT에서 실행되도록 지정
	}

	/**
	 * 액션이 현재 컨텍스트에서 활성화될지 여부를 결정합니다.
	 * 소스 클래스와 대상 클래스 정보가 모두 로드/락온되었을 때 활성화되도록 합니다.
	 */
	@Override
	public void update(@NotNull AnActionEvent e) {
		Project project = e.getData(CommonDataKeys.PROJECT);
		if (project == null) {
			e.getPresentation().setEnabledAndVisible(false);
			return;
		}

		// 플러그인 상태에서 소스 및 대상 클래스 정보 가져오기
		MappingPluginState state = MappingPluginState.getInstance(project);
		boolean isReady = state != null &&
			state.getSourceClassQualifiedName() != null &&
			state.getTargetClassQualifiedName() != null &&
			state.getIncludedTargetFieldNames() != null; // 선택된 필드 목록도 있어야 함

		// 소스 및 대상 클래스 정보가 모두 있을 때만 액션 활성화
		e.getPresentation().setEnabledAndVisible(isReady);
	}

	/**
	 * 액션이 실행될 때의 로직입니다.
	 * Builder 패턴 변환 코드를 생성하고 표시합니다.
	 */
	@Override
	public void actionPerformed(@NotNull AnActionEvent e) {
		// 액션 시스템을 통해 실행될 때의 로직 (버튼 클릭 로직과 동일)
		Project project = e.getData(CommonDataKeys.PROJECT);
		if (project == null)
			return;

		// 분리된 핵심 로직 메소드 호출
		generateCodeAndShow(project);
	}

	/**
	 * 코드 생성 및 표시의 핵심 로직을 수행하는 public static 메소드.
	 * Tool Window의 버튼 클릭 리스너에서 호출됩니다.
	 * @param project 현재 프로젝트
	 */
	public static void generateCodeAndShow(Project project) {
		if (project == null)
			return;

		// 1. 플러그인 상태에서 정보 가져오기
		MappingPluginState state = MappingPluginState.getInstance(project);
		if (state == null) {
			NotificationGroupManager.getInstance().getNotificationGroup("Mapping Plugin Notifications")
				.createNotification("Mapping generation failed", "Plugin state component not found.",
					NotificationType.ERROR)
				.notify(project);
			return;
		}

		String sourceClassQName = state.getSourceClassQualifiedName();
		String targetClassQName = state.getTargetClassQualifiedName();
		List<String> includedTargetFieldNames = state.getIncludedTargetFieldNames();

		// 2. 필요한 정보가 모두 있는지 확인
		if (sourceClassQName == null || targetClassQName == null || includedTargetFieldNames == null) {
			NotificationGroupManager.getInstance().getNotificationGroup("Mapping Plugin Notifications")
				.createNotification("Mapping generation failed", "Please load source and lock on target class first.",
					NotificationType.WARNING)
				.notify(project);
			return;
		}

		// 3. 클래스 이름으로부터 PsiClass 객체 가져오기
		JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
		PsiClass sourceClass = psiFacade.findClass(sourceClassQName, GlobalSearchScope.allScope(project));
		PsiClass targetClass = psiFacade.findClass(targetClassQName, GlobalSearchScope.allScope(project));

		if (sourceClass == null || targetClass == null) {
			NotificationGroupManager.getInstance().getNotificationGroup("Mapping Plugin Notifications")
				.createNotification("Mapping generation failed", "Source or target class not found. Please reload.",
					NotificationType.ERROR)
				.notify(project);
			return;
		}

		// 4. Java 코드 문자열 생성
		String generatedCode = generateMappingMethodCode(sourceClass, targetClass, includedTargetFieldNames, project,
			state.isGenerateMethodComment()); // 이 메소드는 private 유지

		// 5. 생성된 코드 표시 (새 에디터 탭)
		showGeneratedCodeInNewTab(project, generatedCode,
			sourceClass.getName() + "To" + targetClass.getName() + "Mapping.java"); // 이 메소드는 private 유지

		NotificationGroupManager.getInstance().getNotificationGroup("Mapping Plugin Notifications")
			.createNotification("Mapping code generated", "Code is shown in a new tab.", NotificationType.INFORMATION)
			.notify(project);
	}

	public static String generateMappingMethodCode(PsiClass sourceClass, PsiClass targetClass,
		List<String> includedTargetFieldNames, Project project, Boolean generateMethodComment) {
		StringBuilder codeBuilder = new StringBuilder();

		// 1. Import 문 추가 (간단 예시, 실제로는 더 정교하게 처리 필요)
		// 필요한 클래스들의 정규화된 이름을 수집하여 import 문 생성
		// codeBuilder.append("import ").append(targetClass.getQualifiedName()).append(";\n");
		// codeBuilder.append("import ").append(sourceClass.getQualifiedName()).append(";\n");
		// AuditInfo 등 상속 클래스나 사용된 유틸리티 클래스 import도 추가해야 합니다.
		// 예: codeBuilder.append("import kr.amc.amis.customer.feature.shared.AuditInfo;\n");
		// 예: codeBuilder.append("import java.util.List;\n"); // fromList 메소드 생성 시
		// 예: codeBuilder.append("import java.util.Collections;\n"); // fromList 메소드 생성 시
		// 예: codeBuilder.append("import java.util.stream.Collectors;\n"); // fromList 메소드 생성 시
		// 예: codeBuilder.append("import org.springframework.util.StringUtils;\n"); // Getter 이름 추정 시 사용
		// codeBuilder.append("\n");

		// 2. 메소드를 포함할 클래스 시작 (예시: Mapper 인터페이스 또는 클래스)
		// 실제로는 사용자가 선택한 위치에 코드를 삽입하거나, 새 파일을 생성하는 로직이 필요합니다.
		// 여기서는 메소드 코드 자체만 생성합니다.

		// 3. from 메소드 시그니처
		String sourceClassName = sourceClass.getName();
		String targetClassName = targetClass.getName();
		if (generateMethodComment) {
			codeBuilder.append("    /**\n");
			codeBuilder.append("     * ")
				.append(sourceClassName)
				.append(" 객체를 ")
				.append(targetClassName)
				.append(" 객체로 변환합니다.\n");
			codeBuilder.append("     * (이 코드는 DTO MAKER 플러그인에 의해 자동 생성되었습니다.)\n");
			codeBuilder.append("     *\n");
			codeBuilder.append("     * @param source 변환할 ").append(sourceClassName).append(" 객체\n");
			codeBuilder.append("     * @return 변환된 ").append(targetClassName).append(" 객체\n");
			codeBuilder.append("     */\n");
		}
		codeBuilder.append("    public ")
			.append(targetClassName)
			.append(" from(")
			.append(sourceClassName)
			.append(" source) {\n");

		// 4. 소스 객체 null 체크
		codeBuilder.append("        // Handle null source object\n");
		codeBuilder.append("        if (source == null) {\n");
		codeBuilder.append("            return null;\n");
		codeBuilder.append("        }\n\n");

		// 5. 대상 클래스 Builder 호출 시작
		codeBuilder.append("        // Use Builder pattern for target object creation\n");
		codeBuilder.append("        return ").append(targetClassName).append(".builder()\n");

		// 6. 포함된 대상 필드 목록 순회 및 매핑 코드 생성
		// PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project); // 코드 형식 조정 시 사용
		boolean isSourceRecord = sourceClass.isRecord();

		for (String targetFieldName : includedTargetFieldNames) {
			// 대상 클래스에서 필드 찾기
			PsiField targetField = targetClass.findFieldByName(targetFieldName, false);
			if (targetField == null) {
				// 필드를 찾을 수 없는 경우 (이름 변경 등) - 주석 처리
				codeBuilder.append("                // .").append(targetFieldName).append("(...) // TODO: Field '").append(targetFieldName).append("' not found in Source class\n");
				continue;
			}

			// 소스 클래스에서 동일 이름 필드 찾기 (상속 포함)
			PsiField sourceField = sourceClass.findFieldByName(targetFieldName, true);

			if (sourceField != null) {
				PsiType sourceType = sourceField.getType();
				PsiType targetType = targetField.getType();
				// **타입 불일치 주석 문자열 생성**
				String typeMismatchComment = null;
				if (!sourceType.equals(targetType)) {
					typeMismatchComment = " // Source type: " + sourceType.getPresentableText()
						+ ", Target type: " + targetType.getPresentableText()
						+ " => Type conversion needed";
				}
				if (isSourceRecord) {
					codeBuilder.append("                .")
						.append(targetFieldName)
						.append("(source.")
						.append(targetFieldName)
						.append("())");
					if (typeMismatchComment != null) {
						codeBuilder.append(typeMismatchComment);
					}
					codeBuilder.append("\n");
				} else {
					String getterName = "get" + StringUtils.capitalize(targetFieldName); // Spring StringUtils 필요
					codeBuilder.append("                .")
						.append(targetFieldName)
						.append("(source.")
						.append(getterName)
						.append("())");
					if (typeMismatchComment != null) {
						codeBuilder.append(typeMismatchComment);
					}
					codeBuilder.append("\n");
				}
			} else {
				// 소스에 동일 이름 필드가 없는 경우
				// codeBuilder.append("                // .").append(targetFieldName).append("(...) // TODO: Map field '").append(targetFieldName).append("' (no matching field in source)\n");
			}
		}

		// 7. Builder 호출 종료
		codeBuilder.append("                .build();\n");

		// 8. 메소드 종료
		codeBuilder.append("    }\n");

		// 9. 필요하다면 List<SourceClass> -> List<TargetClass> 변환 메소드도 생성 가능
		// codeBuilder.append("\n");
		// codeBuilder.append("    /**\n");
		// codeBuilder.append("     * List<").append(sourceClassName).append("> 객체를 List<").append(targetClassName).append("> 객체로 변환합니다.\n");
		// codeBuilder.append("     *\n");
		// codeBuilder.append("     * @param sourceList 변환할 List<").append(sourceClassName).append("> 객체\n");
		// codeBuilder.append("     * @return 변환된 List<").append(targetClassName).append("> 객체\n");
		// codeBuilder.append("     */\n");
		// codeBuilder.append("    public List<").append(targetClassName).append("> fromList(List<").append(sourceClassName).append("> sourceList) {\n");
		// codeBuilder.append("        if (sourceList == null) {\n");
		// codeBuilder.append("            return null;\n");
		// codeBuilder.append("        }\n");
		// codeBuilder.append("        return sourceList.stream()\n");
		// codeBuilder.append("                .map(this::from) // Use the single object conversion method\n");
		// codeBuilder.append("                .collect(Collectors.toList());\n");
		// codeBuilder.append("    }\n");

		// 10. 코드 형식 조정 (선택 사항)
		// 생성된 코드 문자열을 PSI 요소로 변환하고 IntelliJ 코드 스타일에 맞게 형식을 조정할 수 있습니다.
		// 이 과정은 WriteCommandAction 내에서 수행되어야 합니다.
		// CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
		// PsiElement generatedPsiMethod = elementFactory.createMethodFromText(codeBuilder.toString(), null);
		// PsiElement formattedMethod = codeStyleManager.reformat(generatedPsiMethod);
		// return formattedMethod.getText();

		return codeBuilder.toString(); // 형식 조정 없이 문자열 반환
	}

	/**
	 * 생성된 코드 문자열을 새 에디터 탭에 표시합니다.
	 *
	 * @param project 현재 프로젝트
	 * @param code 생성된 Java 코드 문자열
	 * @param fileName 제안할 파일 이름
	 */
	private static void showGeneratedCodeInNewTab(Project project, String code, String fileName) {
		// 쓰기 작업 내에서 가상 파일 생성 및 에디터 열기
		WriteCommandAction.runWriteCommandAction(project, () -> {
			// 가상 파일 생성
			VirtualFile virtualFile = new LightVirtualFile(fileName,
				FileTypeManager.getInstance().getFileTypeByExtension("java"), code);

			// 새 에디터 탭에 파일 열기
			FileEditorManager.getInstance(project).openFile(virtualFile, true);
		});
	}
}
