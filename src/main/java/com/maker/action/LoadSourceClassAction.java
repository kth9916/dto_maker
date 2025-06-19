package com.maker.action;

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
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.content.Content;
import com.maker.state.MappingPluginState;
import com.maker.ui.MappingToolWindowContentPanel;

/**
 * IntelliJ IDEA 플러그인 액션: 소스 클래스 (변환할 원재료 클래스)를 로드합니다.
 * 컨텍스트 메뉴에서 Java 클래스에 대해 실행됩니다.
 */
public class LoadSourceClassAction extends AnAction {

	// 액션의 표시 이름 (plugin.xml 파일에서 설정할 수도 있습니다)
	public LoadSourceClassAction() {
		super("Loading Bullets"); // 컨텍스트 메뉴에 표시될 이름
	}

	/**
	 * update 메소드가 실행될 스레드를 지정합니다.
	 * PSI 접근은 백그라운드 스레드에서 수행해야 합니다.
	 */
	@Override
	public @NotNull ActionUpdateThread getActionUpdateThread() {
		// PSI 접근은 백그라운드 스레드에서 수행하도록 지정
		return ActionUpdateThread.BGT;
	}
	/**
	 * 액션이 현재 컨텍스트에서 활성화될지 여부를 결정합니다.
	 * Java 클래스 정의 또는 클래스 참조 위에서 실행될 때만 활성화되도록 합니다.
	 */
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
	 * 선택된 Java 클래스를 소스 클래스로 플러그인 상태에 저장합니다.
	 */
	@Override
	public void actionPerformed(@NotNull AnActionEvent e) {
		Project project = e.getData(CommonDataKeys.PROJECT);
		PsiElement psiElement = e.getData(CommonDataKeys.PSI_ELEMENT); // 클릭된 요소

		if (project == null || psiElement == null) {
			// update 메소드에서 걸러지지만, 안전을 위해 다시 체크
			notify(project, "Mapping generation failed", "Project or selected element is null.",
				NotificationType.ERROR);
			return;
		}

		PsiClass sourceClass = null;

		if (psiElement instanceof PsiClass) {
			// psiElement 자체가 PsiClass인 경우
			sourceClass = (PsiClass)psiElement;
		} else {
			// psiElement가 PsiClass가 아닌 경우
			PsiElement targetElement = PsiTreeUtil.getParentOfType(psiElement, PsiJavaCodeReferenceElement.class); // PsiClass.class는 여기서 제외
			if (targetElement instanceof PsiJavaCodeReferenceElement) {
				PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)targetElement;
				PsiElement resolvedElement = referenceElement.resolve();
				if (resolvedElement instanceof PsiClass) {
					sourceClass = (PsiClass)resolvedElement;
				}
			}
		}

		// 소스 클래스를 찾지 못했으면 오류 알림
		if (sourceClass == null) {
			notify(project, "Mapping generation failed",
				"Could not find a valid source class from the selected element.", NotificationType.ERROR);
			return;
		}

		// 1. 소스 클래스 정보 저장 (플러그인 상태 컴포넌트 사용)
		MappingPluginState state = MappingPluginState.getInstance(project);
		if (state != null) {
			String qualifiedName = sourceClass.getQualifiedName();
			if (qualifiedName != null) { // qualifiedName이 null이 아닐 때만 저장 및 알림
				state.setSourceClassQualifiedName(qualifiedName); // 클래스 정규화된 이름 저장

				// 2. 사용자에게 알림
				notify(project, "Source class loaded", "Loaded: " + qualifiedName, NotificationType.INFORMATION);

				// 3. 플러그인 UI 업데이트 (Tool Window)
				updateToolWindowUI(project, qualifiedName);

			} else {
				// qualifiedName이 null인 경우 (예: 익명 클래스)
				notify(project, "Mapping generation failed", "Selected class does not have a qualified name.",
					NotificationType.ERROR);
			}

		} else {
			notify(project, "Mapping generation failed", "Plugin state component not found.", NotificationType.ERROR);
		}
	}

	private void notify(Project project, String title, String content, NotificationType type) {
		//
		NotificationGroupManager.getInstance().getNotificationGroup("Mapping Plugin Notifications")
			.createNotification(title, content,
				type)
			.notify(project);
	}

	private void updateToolWindowUI(Project project, String qualifiedName) {
		ToolWindow toolWindow = ToolWindowManager.getInstance(project)
			.getToolWindow("DTO Maker"); // plugin.xml에 등록한 ID 사용
		if (toolWindow != null) {
			// Tool Window가 열려있지 않으면 열기 (선택 사항)
			// toolWindow.activate(null);

			// Tool Window의 ContentManager에서 UI Content 가져오기
			Content content = toolWindow.getContentManager().getContent(0); // 첫 번째 Content (UI 패널)
			if (content != null) {
				// Content에서 실제 UI 컴포넌트 가져오기
				JComponent component = content.getComponent();
				// UI 컴포넌트가 우리가 만든 MappingToolWindowContentPanel 타입인지 확인하고 캐스팅
				if (component instanceof MappingToolWindowContentPanel) {
					MappingToolWindowContentPanel uiPanel = (MappingToolWindowContentPanel)component;
					// UI 패널의 업데이트 메소드 호출
					uiPanel.updateSourceClassLabel(qualifiedName);
				}
			}
		}
	}
}
