package com.maker.action;

import java.util.ArrayList;
import java.util.List;

import javax.swing.*;

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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.content.Content;
import com.maker.state.MappingPluginState;
import com.maker.ui.MappingToolWindowContentPanel;

/**
 * IntelliJ IDEA 플러그인 액션: 대상 클래스 (변환 결과 클래스)를 락온하고 필드를 선택합니다.
 * 컨텍스트 메뉴에서 Java 클래스에 대해 실행됩니다.
 */
public class LockOnTargetClassAction extends AnAction {

	public LockOnTargetClassAction() {
		super("Lock On"); // 컨텍스트 메뉴에 표시될 이름
	}

	@Override
	public @NotNull ActionUpdateThread getActionUpdateThread() {
		// update 메소드는 백그라운드 스레드에서 실행
		return ActionUpdateThread.BGT;
	}

	/**
	 * 액션이 현재 컨텍스트에서 활성화될지 여부를 결정합니다.
	 * Java 클래스 위에서 실행될 때만 활성화되도록 합니다.
	 */
	@Override
	public void update(@NotNull AnActionEvent e) {
		Project project = e.getData(CommonDataKeys.PROJECT);
		PsiElement psiElement = e.getData(CommonDataKeys.PSI_ELEMENT);

		boolean isActionEnabled = false;
		if (project != null && psiElement != null) {
			if (psiElement instanceof PsiClass) {
				// psiElement 자체가 PsiClass인 경우 (클래스 정의 클릭)
				isActionEnabled = true;
			} else {
				// psiElement가 PsiClass가 아닌 경우 (클래스 참조 등)
				PsiElement targetElement = PsiTreeUtil.getParentOfType(psiElement, PsiJavaCodeReferenceElement.class); // PsiClass.class는 여기서 제외
				if (targetElement instanceof PsiJavaCodeReferenceElement) {
					PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)targetElement;
					PsiElement resolvedElement = referenceElement.resolve();
					if (resolvedElement instanceof PsiClass) {
						isActionEnabled = true;
					}
				}
			}
		}
		e.getPresentation().setEnabledAndVisible(isActionEnabled);
	}

	/**
	 * 액션이 실행될 때의 로직입니다.
	 * 선택된 Java 클래스를 대상 클래스로 플러그인 상태에 저장하고 필드 선택 UI를 표시합니다.
	 */
	@Override
	public void actionPerformed(@NotNull AnActionEvent e) {
		Project project = e.getData(CommonDataKeys.PROJECT);
		PsiElement psiElement = e.getData(CommonDataKeys.PSI_ELEMENT); // 클릭된 요소

		if (project == null || psiElement == null) {
			// update 메소드에서 걸러지지만, 안전을 위해 다시 체크
			notify(project, "Lock On Target Failed", "Project or selected element is null.",
				NotificationType.WARNING);
			return;
		}

		// PsiFile을 통해 PsiClass 가져오기 (Load 액션과 동일)
		PsiClass targetClass = null;

		if (psiElement instanceof PsiClass) {
			// psiElement 자체가 PsiClass인 경우
			targetClass = (PsiClass)psiElement;
		} else {
			// psiElement가 PsiClass가 아닌 경우
			PsiElement targetElement = PsiTreeUtil.getParentOfType(psiElement, PsiJavaCodeReferenceElement.class); // PsiClass.class는 여기서 제외
			if (targetElement instanceof PsiJavaCodeReferenceElement) {
				PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)targetElement;
				PsiElement resolvedElement = referenceElement.resolve();
				if (resolvedElement instanceof PsiClass) {
					targetClass = (PsiClass)resolvedElement;
				}
			}
		}

		if (targetClass == null) {
			NotificationGroupManager.getInstance().getNotificationGroup("Mapping Plugin Notifications")
				.createNotification("Lock On Target Failed", "Please select a Java class file.",
					NotificationType.WARNING)
				.notify(project);
			return;
		}

		// 1. 대상 클래스 정보 저장
		MappingPluginState state = MappingPluginState.getInstance(project);
		if (state == null) {
			NotificationGroupManager.getInstance().getNotificationGroup("Mapping Plugin Notifications")
				.createNotification("Lock On Target Failed", "Plugin state component not found.",
					NotificationType.ERROR)
				.notify(project);
			return;
		}

		String qualifiedName = targetClass.getQualifiedName();
		state.setTargetClassQualifiedName(qualifiedName); // 클래스 정규화된 이름 저장

		// 2. 대상 클래스의 모든 필드 이름 추출 (상속 포함)
		List<String> allFieldNames = new ArrayList<>();
		for (PsiField field : targetClass.getAllFields()) { // getAllFields()는 상속받은 필드 포함
			allFieldNames.add(field.getName());
		}

		// 3. 필드 선택 UI 표시 (JDialog 등 사용)
		// 이 부분은 Swing 또는 IntelliJ UI 컴포넌트를 사용하여 JDialog를 만들고
		// allFieldNames 목록을 체크박스나 JList로 보여주는 복잡한 UI 코드입니다.
		// 사용자 UI에서 선택된 필드 이름 목록을 얻었다고 가정합니다.
		// showFieldSelectionDialog 메소드는 UI 로직으로, 여기서 직접 구현하지 않습니다.
		// JDialog, JPanel, JList/JCheckBox, JButton 등을 사용하여 구현해야 합니다.
		// 선택된 필드 목록을 반환하는 메소드를 호출합니다.
		List<String> selectedFieldNamesFromUI = showFieldSelectionDialog(project,
			allFieldNames); // <-- UI 로직 호출 (구현 필요)

		// 4. 선택된 필드 이름 목록 저장
		state.setIncludedTargetFieldNames(selectedFieldNamesFromUI);

		// 5. 사용자에게 알림
		NotificationGroupManager.getInstance().getNotificationGroup("Mapping Plugin Notifications")
			.createNotification("Target class locked on",
				"Locked On: " + qualifiedName + " with " + selectedFieldNamesFromUI.size() + " fields selected",
				NotificationType.INFORMATION)
			.notify(project);

		// 6. 플러그인 UI 업데이트 (Tool Window)
		ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("DTO Maker");
		if (toolWindow != null) {
			// toolWindow.activate(null); // 필요시 주석 해제

			Content content = toolWindow.getContentManager().getContent(0);
			if (content != null) {
				JComponent component = content.getComponent();
				if (component instanceof MappingToolWindowContentPanel) {
					MappingToolWindowContentPanel uiPanel = (MappingToolWindowContentPanel)component;
					uiPanel.updateTargetClassLabel(qualifiedName);
					uiPanel.updateSelectedFieldsList(
						selectedFieldNamesFromUI); // <-- 선택된 필드 목록 표시 메소드 (MappingToolWindowContentPanel에 추가 필요)
				}
			}
		}
	}

	/**
	 * 대상 클래스의 필드 목록을 보여주고 사용자가 선택할 수 있는 다이얼로그를 표시합니다.
	 * 이 메소드는 UI 로직이므로 여기서 직접 구현하지 않습니다.
	 * JDialog, JPanel, JCheckBox 등을 사용하여 구현해야 합니다.
	 * @param project 현재 프로젝트
	 * @param fieldNames 대상 클래스의 모든 필드 이름 목록
	 * @return 사용자가 선택한 필드 이름 목록
	 */
	private List<String> showFieldSelectionDialog(Project project, List<String> fieldNames) {
		// TODO: 필드 선택 UI 다이얼로그 구현
		// 예시: 간단히 모든 필드를 선택된 것으로 반환 (실제 UI 로직으로 대체 필요)
		// 실제 구현에서는 JDialog를 띄우고, fieldNames를 체크박스 목록으로 보여준 후
		// 사용자가 확인 버튼을 눌렀을 때 체크된 항목들의 이름을 리스트로 반환해야 합니다.

		// 임시 구현: 모든 필드를 선택된 것으로 간주
		return fieldNames;
	}

	private void notify(Project project, String title, String content, NotificationType type) {
		//
		NotificationGroupManager.getInstance().getNotificationGroup("Mapping Plugin Notifications")
			.createNotification(title, content,
				type)
			.notify(project);
	}
}
