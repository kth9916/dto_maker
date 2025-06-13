package com.maker.action;

import java.util.List;

import javax.swing.*;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.content.Content;
import com.maker.state.MappingPluginState;
import com.maker.ui.MappingToolWindowContentPanel;

/**
 * IntelliJ IDEA 플러그인 액션: 로드된 소스 클래스와 락온된 대상 클래스 정보를 바탕으로
 * Getter/Setter 변환 코드를 생성하고 사용자에게 보여줍니다.
 */
public class GenerateGetterSetterMappingCodeAction extends AnAction {

	public GenerateGetterSetterMappingCodeAction() {
		super("Generate Getter/Setter Mapping Code"); // 버튼/메뉴에 표시될 이름
	}

	// 이 액션도 Tool Window 버튼에서 직접 호출될 것이므로 update/actionPerformed는 간단하게 유지
	// 또는 액션 시스템을 통해 실행될 때의 로직을 구현

	@Override
	public @NotNull ActionUpdateThread getActionUpdateThread() {
		return ActionUpdateThread.EDT; // 또는 BGT (로직에 따라)
	}

	@Override
	public void update(@NotNull AnActionEvent e) {
		// Builder 패턴 생성 액션과 유사하게 소스/대상 클래스 정보가 있을 때 활성화
		Project project = e.getData(CommonDataKeys.PROJECT);
		if (project == null) {
			e.getPresentation().setEnabledAndVisible(false);
			return;
		}
		MappingPluginState state = MappingPluginState.getInstance(project);
		boolean isReady = state != null &&
			state.getSourceClassQualifiedName() != null &&
			state.getTargetClassQualifiedName() != null &&
			state.getIncludedTargetFieldNames() != null;
		e.getPresentation().setEnabledAndVisible(isReady);
	}

	@Override
	public void actionPerformed(@NotNull AnActionEvent e) {
		Project project = e.getData(CommonDataKeys.PROJECT);
		if (project == null)
			return;

		// 분리된 핵심 로직 메소드 호출
		generateCodeAndShow(project);
	}

	/**
	 * Getter/Setter 변환 코드 생성 및 표시의 핵심 로직을 수행하는 public static 메소드.
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

		// **4. Getter/Setter Java 코드 문자열 생성**
		String generatedCode = generateGetterSetterMappingMethodCode(sourceClass, targetClass, includedTargetFieldNames,
			project, state.isGenerateMethodComment()); // <-- 새로운 코드 생성 메소드 호출

		// 5. 생성된 코드를 UI에 표시 (Tool Window)
		// Tool Window UI 컴포넌트를 찾아 setGeneratedCode 메소드 호출
		ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("DTO Maker");
		if (toolWindow != null) {
			Content content = toolWindow.getContentManager().getContent(0);
			if (content != null) {
				JComponent component = content.getComponent();
				if (component instanceof MappingToolWindowContentPanel) {
					MappingToolWindowContentPanel uiPanel = (MappingToolWindowContentPanel)component;
					uiPanel.setGeneratedCode(generatedCode); // <-- UI 업데이트
				}
			}
		}

		NotificationGroupManager.getInstance().getNotificationGroup("Mapping Plugin Notifications")
			.createNotification("Getter/Setter code generated", "Code is shown in the tool window.",
				NotificationType.INFORMATION)
			.notify(project);
	}

	/**
	 * Getter/Setter 변환 메소드의 Java 코드 문자열을 생성합니다.
	 * Record 타입 소스 클래스도 고려하며, 타입 불일치 시 주석을 추가합니다.
	 *
	 * @param sourceClass 소스 PsiClass
	 * @param targetClass 대상 PsiClass
	 * @param includedTargetFieldNames 포함할 대상 필드 이름 목록
	 * @param project 현재 프로젝트
	 * @return 생성된 Java 코드 문자열
	 */
	public static String generateGetterSetterMappingMethodCode(PsiClass sourceClass, PsiClass targetClass,
		List<String> includedTargetFieldNames, Project project, Boolean generateMethodComment) {
		StringBuilder codeBuilder = new StringBuilder();

		// 1. Import 문 추가 (Builder 패턴 생성과 유사)
		// codeBuilder.append("import ").append(targetClass.getQualifiedName()).append(";\n");
		// codeBuilder.append("import ").append(sourceClass.getQualifiedName()).append(";\n");
		// ... 필요한 다른 import들 ...
		// codeBuilder.append("\n");

		// 2. 메소드 시그니처 (예: public TargetClass from(SourceClass source))
		String sourceClassName = sourceClass.getName();
		String sourceUncapitalizedName = StringUtils.uncapitalize(sourceClassName);
		String targetClassName = targetClass.getName();
		String targetUncapitalizedName = StringUtils.uncapitalize(targetClassName);
		if (generateMethodComment) {
			codeBuilder.append("    /**\n");
			codeBuilder.append("     * ")
				.append(sourceClassName)
				.append(" 객체를 ")
				.append(targetClassName)
				.append(" 객체로 변환합니다.\n");
			codeBuilder.append("     * (이 코드는 DTO MAKER 플러그인에 의해 자동 생성되었습니다 - Getter/Setter).\n");
			codeBuilder.append("     *\n");
			codeBuilder.append("     * @param source 변환할 ").append(sourceClassName).append(" 객체\n");
			codeBuilder.append("     * @return 변환된 ").append(targetClassName).append(" 객체\n");
			codeBuilder.append("     */\n");
		}
		codeBuilder.append("    public ")
			.append(targetClassName)
			.append(" from(")
			.append(sourceClassName)
			.append(" ")
			.append(sourceUncapitalizedName).append(") {\n");

		// 3. 소스 객체 null 체크
		codeBuilder.append("        // Handle null source object\n");
		codeBuilder.append("        if (").append(sourceUncapitalizedName).append(" == null) {\n");
		codeBuilder.append("            return null;\n");
		codeBuilder.append("        }\n\n");

		// 4. 대상 객체 생성
		// BeanUtils.instantiateClass(targetType)와 유사하게 기본 생성자로 객체 생성
		codeBuilder.append("    ")
			.append(targetClassName)
			.append(" ")
			.append(targetUncapitalizedName)
			.append(" = new ")
			.append(targetClassName)
			.append("();\n\n");

		// 5. 포함된 대상 필드 목록 순회 및 Getter/Setter 호출 코드 생성
		PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project); // 코드 형식 조정 시 사용
		boolean isSourceRecord = sourceClass.isRecord();

		for (String targetFieldName : includedTargetFieldNames) {
			// 대상 클래스에서 필드 찾기
			PsiField targetField = targetClass.findFieldByName(targetFieldName, false);
			if (targetField == null) {
				// 필드를 찾을 수 없는 경우 - 주석 처리
				codeBuilder
					.append("        ")
					.append("// ")
					.append(targetUncapitalizedName)
					.append(".")
					.append("set")
					.append(StringUtils.capitalize(targetFieldName))
					.append("(...)")
					.append("    // TODO: Field '")
					.append(targetFieldName)
					.append("' not found in ")
					.append(sourceClassName)
					.append(" class\n");
				continue;
			}

			// 소스 클래스에서 동일 이름 필드 찾기 (상속 포함)
			PsiField sourceField = sourceClass.findFieldByName(targetFieldName, true);

			if (sourceField != null) {
				// 소스에 동일 이름 필드가 있는 경우
				String accessorCall; // 소스 필드 접근자 호출 문자열
				PsiType sourceType = sourceField.getType(); // 소스 필드 타입
				PsiType targetType = targetField.getType(); // 대상 필드 타입

				// 타입 불일치 주석 문자열 생성
				String typeMismatchComment = null;
				if (!sourceType.equals(targetType)) {
					typeMismatchComment = " // Source type: " + sourceType.getPresentableText()
						+ ", Target type: " + targetType.getPresentableText()
						+ " => Type conversion needed";
				}

				// 소스 필드 접근자 호출 코드 생성 (Record vs Class)
				if (isSourceRecord) {
					accessorCall = sourceUncapitalizedName + "." + targetFieldName + "()";
				} else {
					// Class: Getter 메소드
					String presumedGetterName = "get" + StringUtils.capitalize(targetFieldName);
					accessorCall = sourceUncapitalizedName + "." + presumedGetterName + "()";
				}

				// 대상 클래스의 Setter 메소드 찾기
				// PropertyUtilBase.findPropertySetter 사용

				codeBuilder.append("        ")
					.append(targetUncapitalizedName)
					.append(".")
					.append("set")
					.append(StringUtils.capitalize(targetFieldName))
					.append("(")
					.append(accessorCall)
					.append(");");
				// 타입 불일치 주석도 함께 추가
				if (typeMismatchComment != null) {
					codeBuilder.append(typeMismatchComment);
				}
				codeBuilder.append("\n");

			} else {
				// 소스에 동일 이름 필드가 없는 경우
				// codeBuilder.append("        // target.").append("set").append(StringUtils.capitalize(targetFieldName)).append("(...); // TODO: Map field '").append(targetFieldName).append("' (no matching field in source)\n");
			}
		}

		// 6. 대상 객체 반환
		codeBuilder.append("\n        return ").append(targetUncapitalizedName).append(";\n");

		// 7. 메소드 종료
		codeBuilder.append("    }\n");

		// ... (showGeneratedCodeInNewTab 메소드 - 필요없음) ...
		// 10. 코드 형식 조정 (선택 사항)
		return codeBuilder.toString();
	}
	// ... (generateCodeAndShow 메소드 - 필요없음) ...
}
