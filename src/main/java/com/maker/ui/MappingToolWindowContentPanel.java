package com.maker.ui;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.apache.commons.lang3.StringUtils;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;
import com.maker.action.GenerateGetterSetterMappingCodeAction;
import com.maker.action.GenerateMappingCodeAction;
import com.maker.state.MappingPluginState;

/**
 * Mapping Plugin Tool Window의 실제 UI 패널입니다.
 * 로드된 클래스 이름 등을 표시하고, 코드 생성 버튼, 필드 목록 및 제거 기능, 생성된 코드 표시 영역을 포함합니다.
 */
public class MappingToolWindowContentPanel extends JPanel {

	private final Project project;

	// UI 요소들
	private final JBLabel sourceClassLabel;
	private final JBLabel targetClassLabel;
	private final JBLabel selectedFieldsLabel;
	private final DefaultListModel<String> fieldListModel;
	private final JList<String> selectedFieldsList;

	private final JButton removeFieldButton;
	private final JButton generateBuilderButton;
	private final JButton generateGetterSetterButton;
	private final JButton copyButton;

	private final JCheckBox generateListMethodCheckBox;
	private final JCheckBox generateMethodCommentCheckBox;

	// **생성된 코드를 표시할 UI 요소**
	private final JTextArea generatedCodeArea; // <-- 생성된 코드 표시 텍스트 영역
	private final JBLabel generatedCodeLabel; // <-- 생성된 코드 영역 레이블

	public MappingToolWindowContentPanel(Project project) {
		this.project = project;

		// UI 요소 초기화
		sourceClassLabel = new JBLabel("Source Class: Not Loaded");
		targetClassLabel = new JBLabel("Target Class: Not Locked On");
		selectedFieldsLabel = new JBLabel("Selected Fields:");

		fieldListModel = new DefaultListModel<>();
		selectedFieldsList = new JList<>(fieldListModel);
		selectedFieldsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		JScrollPane listScrollPane = new JScrollPane(selectedFieldsList);
		listScrollPane.setPreferredSize(new Dimension(400, 400));

		removeFieldButton = new JButton("Remove Selected Field");

		generateBuilderButton = new JButton("Generate Builder Code");
		generateGetterSetterButton = new JButton("Generate Getter/Setter Code");

		// **생성된 코드 표시 영역 초기화**
		generatedCodeLabel = new JBLabel("Generated Code:");
		generatedCodeArea = new JTextArea(30, 40); // 10줄, 40열 텍스트 영역 (크기 조정 필요)
		generatedCodeArea.setEditable(false); // 편집 불가능
		generatedCodeArea.setLineWrap(true); // 자동 줄바꿈
		generatedCodeArea.setWrapStyleWord(true); // 단어 단위 줄바꿈
		JScrollPane codeScrollPane = new JScrollPane(generatedCodeArea); // 스크롤 가능하도록 JScrollPane 추가
		codeScrollPane.setPreferredSize(new Dimension(400, 600));
		copyButton = new JButton("Copy Code");

		generateListMethodCheckBox = new JCheckBox("Generate List Conversion Method");
		generateMethodCommentCheckBox = new JCheckBox("Include Method Comment");

		MappingPluginState state = MappingPluginState.getInstance(project);
		if (state != null) {
			if (state.isGenerateListMethod() != null) {
				generateListMethodCheckBox.setSelected(state.isGenerateListMethod());
			} else {
				generateListMethodCheckBox.setSelected(false); // 기본값
			}
			// **메소드 주석 체크박스 상태 로드**
			if (state.isGenerateMethodComment() != null) { // <-- 새로운 상태 필드 사용
				generateMethodCommentCheckBox.setSelected(state.isGenerateMethodComment());
			} else {
				generateMethodCommentCheckBox.setSelected(true); // 기본값: 주석 포함
			}
		} else {
			// 상태 로드 실패 시 기본값 설정
			generateListMethodCheckBox.setSelected(false);
			generateMethodCommentCheckBox.setSelected(true);
		}

		JPanel copyButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		copyButtonPanel.add(copyButton);
		// UI 레이아웃 구성 (FormBuilder 사용 예시)
		// **UI 레이아웃 구성 (BorderLayout 활용)**
		// 상단 영역을 담을 패널 생성 (FormBuilder 사용)
		FormBuilder builder = FormBuilder.createFormBuilder()
			.addLabeledComponent("Source:", sourceClassLabel)
			.addLabeledComponent("Target:", targetClassLabel)
			.addLabeledComponent(selectedFieldsLabel, listScrollPane) // 필드 목록 스크롤 패널 추가
			.addComponent(createButtonPanel())
			.addComponent(generateListMethodCheckBox)
			.addComponent(generateMethodCommentCheckBox)
			.addComponent(generatedCodeLabel)
			.addComponent(codeScrollPane)
			.addComponent(copyButtonPanel)
			.addComponentFillVertically(new JPanel(), 0);// 남은 공간 채우는 컴포넌트 추가 (선택 사항)

		// 메인 패널의 레이아웃을 BorderLayout으로 설정
		setLayout(new BorderLayout());

		// 상단 패널을 NORTH 영역에 추가
		add(builder.getPanel(), BorderLayout.NORTH);

		// **버튼에 ActionListener 추가**
		generateBuilderButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				// Shot 기능 핵심 로직 호출
				// 이 메소드에서 생성된 코드를 UI에 설정하도록 연결해야 합니다.
				// GenerateMappingCodeAction.generateCodeAndShow(project); // <-- 이 호출 대신 아래 로직 사용
				generateCodeAndDisplay(project, CodeType.BUILDER); // <-- 새로 만든 메소드 호출
			}
		});

		// Getter/Setter 패턴 생성 버튼 리스너
		generateGetterSetterButton.addActionListener(new ActionListener() { // <-- 새로운 버튼 리스너
			@Override
			public void actionPerformed(ActionEvent e) {
				// Getter/Setter 패턴 생성 핵심 로직 호출
				generateCodeAndDisplay(project, CodeType.GETTER_SETTER); // <-- 새로 만든 메소드 호출
				// GenerateGetterSetterMappingCodeAction.generateCodeAndShow(project); // <-- 새로운 Getter/Setter 생성 메소드 호출
			}
		});

		// **Copy 버튼에 ActionListener 추가**
		copyButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				// 클립보드 복사 로직 실행
				copyGeneratedCodeToClipboard();
			}
		});

		// **필드 제거 버튼에 ActionListener 추가**
		removeFieldButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				// ... (기존 필드 제거 로직) ...
				int selectedIndex = selectedFieldsList.getSelectedIndex();
				if (selectedIndex != -1) {
					String removedFieldName = fieldListModel.getElementAt(selectedIndex);
					fieldListModel.removeElementAt(selectedIndex);

					MappingPluginState state = MappingPluginState.getInstance(project);
					if (state != null) {
						List<String> currentSelectedFields = state.getIncludedTargetFieldNames();
						if (currentSelectedFields != null) {
							currentSelectedFields.remove(removedFieldName);
							state.setIncludedTargetFieldNames(currentSelectedFields);
						}
					}
					NotificationGroupManager.getInstance().getNotificationGroup("Mapping Plugin Notifications")
						.createNotification("Field Removed", "Removed: " + removedFieldName,
							NotificationType.INFORMATION)
						.notify(project);
				}
			}
		});

		// **List 변환 체크박스에 ActionListener 추가**
		generateListMethodCheckBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				// 체크박스 상태 변경 시 플러그인 상태 업데이트
				MappingPluginState state = MappingPluginState.getInstance(project);
				if (state != null) {
					state.setGenerateListMethod(generateListMethodCheckBox.isSelected());
				}
			}
		});

		// **메소드 주석 체크박스에 ActionListener 추가**
		generateMethodCommentCheckBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				MappingPluginState state = MappingPluginState.getInstance(project);
				if (state != null) {
					state.setGenerateMethodComment(generateMethodCommentCheckBox.isSelected()); // <-- 새로운 상태 필드 업데이트
				}
			}
		});

		// JList 선택 변경 리스너
		selectedFieldsList.addListSelectionListener(new ListSelectionListener() {
			@Override
			public void valueChanged(ListSelectionEvent e) {
				if (!e.getValueIsAdjusting()) {
					removeFieldButton.setEnabled(selectedFieldsList.getSelectedIndex() != -1);
				}
			}
		});
		removeFieldButton.setEnabled(false);
		copyButton.setEnabled(false);
	}

	/**
	 * Remove Selected Field 버튼과 Generate Mapping Code 버튼을 담을 패널을 생성합니다.
	 * @return 버튼 패널
	 */
	private JPanel createButtonPanel() {
		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT)); // 버튼을 왼쪽에 정렬
		buttonPanel.add(removeFieldButton);
		buttonPanel.add(generateBuilderButton);
		buttonPanel.add(generateGetterSetterButton);
		return buttonPanel;
	}

	public void updateSourceClassLabel(String className) {
		sourceClassLabel.setText("Source Class: " + className);
	}

	public void updateTargetClassLabel(String className) {
		targetClassLabel.setText("Target Class: " + className);
	}

	public void updateSelectedFieldsList(List<String> fieldNames) {
		fieldListModel.clear();
		if (fieldNames != null) {
			for (String fieldName : fieldNames) {
				fieldListModel.addElement(fieldName);
			}
		}
		removeFieldButton.setEnabled(selectedFieldsList.getSelectedIndex() != -1);
	}

	/**
	 * 생성된 코드 문자열을 UI의 텍스트 영역에 설정합니다.
	 * @param code 생성된 Java 코드 문자열
	 */
	public void setGeneratedCode(String code) {
		generatedCodeArea.setText(code);
		generatedCodeArea.setCaretPosition(0); // 스크롤을 맨 위로 이동
		copyButton.setEnabled(true);
	}

	/**
	 * Shot 기능의 핵심 로직을 수행하고 생성된 코드를 UI에 표시합니다.
	 * GenerateMappingCodeAction의 generateCodeAndShow 메소드 로직을 가져와서 수정합니다.
	 * @param project 현재 프로젝트
	 */
	private void generateCodeAndDisplay(Project project, CodeType codeType) {
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
		boolean generateListMethod = state.isGenerateListMethod() != null ? state.isGenerateListMethod() : false;
		boolean generateMethodComment =
			state.isGenerateMethodComment() != null ? state.isGenerateMethodComment() : true;
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

		// **4. 코드 문자열 생성 (단일 객체 변환 메소드)**
		String singleMethodCode;
		if (codeType == CodeType.BUILDER) {
			singleMethodCode = GenerateMappingCodeAction.generateMappingMethodCode(sourceClass, targetClass,
				includedTargetFieldNames, project, generateMethodComment); // Builder 패턴 생성
		} else { // codeType == CodeType.GETTER_SETTER
			singleMethodCode = GenerateGetterSetterMappingCodeAction.generateGetterSetterMappingMethodCode(sourceClass,
				targetClass, includedTargetFieldNames, project, generateMethodComment); // Getter/Setter 패턴 생성
		}
		// **5. List 변환 메소드 코드 생성 (체크박스 선택 시)**
		String listMethodCode = null;
		if (generateListMethod) {
			// List 변환 메소드 코드 생성 로직 호출
			listMethodCode = generateListConversionMethodCode(sourceClass, targetClass, project,
				generateMethodComment); // <-- 새로운 메소드 호출
		}

		// **6. 전체 코드 문자열 조합 (단일 + List)**
		StringBuilder fullCodeBuilder = new StringBuilder();
		fullCodeBuilder.append(singleMethodCode); // 단일 객체 변환 메소드 추가
		if (listMethodCode != null) {
			fullCodeBuilder.append("\n"); // 메소드 사이에 줄바꿈 추가
			fullCodeBuilder.append(listMethodCode); // List 변환 메소드 추가
		}
		String combinedCode = fullCodeBuilder.toString(); // 조합된 코드

		// **7. 생성된 코드 형식 조정**
		// 전체 코드를 형식 조정합니다. 파일 코드로 파싱합니다.
		// String formattedCode = GenerateMappingCodeAction.reformatCodeString(project, combinedCode, false, null); // <-- 파일 코드로 형식 조정

		// **8. 생성된 코드를 UI의 텍스트 영역에 설정**
		setGeneratedCode(combinedCode); // <-- 형식 조정된 코드 UI에 설정

		NotificationGroupManager.getInstance().getNotificationGroup("Mapping Plugin Notifications")
			.createNotification("Mapping code generated", "Code is shown in the tool window.",
				NotificationType.INFORMATION)
			.notify(project);
	}

	/**
	 * generatedCodeArea의 텍스트 내용을 시스템 클립보드에 복사합니다.
	 */
	private void copyGeneratedCodeToClipboard() {
		String codeToCopy = generatedCodeArea.getText(); // 텍스트 영역의 내용 가져오기

		if (codeToCopy != null && !codeToCopy.isEmpty()) {
			try {
				// 시스템 클립보드 가져오기
				Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
				// 복사할 문자열을 StringSelection 객체로 감싸기
				StringSelection stringSelection = new StringSelection(codeToCopy);
				// 클립보드에 내용 설정
				clipboard.setContents(stringSelection, null);

				// 사용자에게 복사 완료 알림 (선택 사항)
				NotificationGroupManager.getInstance().getNotificationGroup("Mapping Plugin Notifications")
					.createNotification("Code Copied", "Generated code copied to clipboard.",
						NotificationType.INFORMATION)
					.notify(project);

			} catch (HeadlessException ex) {
				// 그래픽 환경이 아닌 경우 (서버 등) 클립보드 접근 시 발생 가능
				NotificationGroupManager.getInstance().getNotificationGroup("Mapping Plugin Notifications")
					.createNotification("Copy Failed", "Clipboard access failed.", NotificationType.ERROR)
					.notify(project);
				ex.printStackTrace();
			}
		} else {
			// 복사할 코드가 없는 경우
			NotificationGroupManager.getInstance().getNotificationGroup("Mapping Plugin Notifications")
				.createNotification("Copy Failed", "No code to copy.", NotificationType.WARNING)
				.notify(project);
		}
	}

	/**
	 * List<Source> -> List<Target> 변환 메소드의 Java 코드 문자열을 생성합니다.
	 *
	 * @param sourceClass 소스 PsiClass
	 * @param targetClass 대상 PsiClass
	 * @param project 현재 프로젝트
	 * @return 생성된 List 변환 메소드 코드 문자열
	 */
	private static String generateListConversionMethodCode(PsiClass sourceClass, PsiClass targetClass, Project project,
		Boolean generateMethodComment) {
		StringBuilder codeBuilder = new StringBuilder();

		String sourceClassName = sourceClass.getName();
		String sourceUncapitalizedName = StringUtils.uncapitalize(sourceClassName);
		String targetClassName = targetClass.getName();
		String targetUncapitalizedName = StringUtils.uncapitalize(targetClassName);

		String sourceListName = sourceUncapitalizedName + "List";

		// 메소드 시그니처 주석
		if (generateMethodComment) {
			codeBuilder.append("    /**\n");
			codeBuilder.append("     * List<")
				.append(sourceClassName)
				.append("> 객체를 List<")
				.append(targetClassName)
				.append("> 객체로 변환합니다.\n");
			codeBuilder.append("     *\n");
			codeBuilder.append("     * @param sourceList 변환할 List<").append(sourceClassName).append("> 객체\n");
			codeBuilder.append("     * @return 변환된 List<").append(targetClassName).append("> 객체\n");
			codeBuilder.append("     */\n");
		}
		// 메소드 시그니처
		codeBuilder.append("    public List<")
			.append(targetClassName)
			.append("> fromList(List<")
			.append(sourceClassName)
			.append("> ")
			.append(sourceListName)
			.append(") {\n");

		// null 체크
		codeBuilder.append("        // Handle null source list\n");
		codeBuilder
			.append("        if (")
			.append(sourceListName)
			.append(" == null) {\n")
			;
		codeBuilder.append("            return null;\n");
		codeBuilder.append("        }\n\n");

		// 빈 리스트 처리 (선택 사항)
		codeBuilder.append("        // Handle empty source list\n");
		codeBuilder
			.append("        if (")
			.append(sourceListName)
			.append(".isEmpty()) {\n");
		codeBuilder.append("            return java.util.Collections.emptyList(); \n");
		codeBuilder.append("        }\n\n");

		// 스트림을 사용하여 변환
		codeBuilder
			.append("        return ")
			.append(sourceListName)
			.append(".stream()\n");
		codeBuilder.append(
			"                .map(this::from) // Use the single object conversion method\n"); // <-- 단일 객체 변환 메소드 호출
		codeBuilder.append("                .collect(java.util.stream.Collectors.toList()); \n");

		// 메소드 종료
		codeBuilder.append("    }\n");

		return codeBuilder.toString();
	}

	// 코드 타입 구분을 위한 Enum
	private enum CodeType {
		BUILDER,
		GETTER_SETTER
	}
}
