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
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
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
	 * Java 클래스 위에서 실행될 때만 활성화되도록 합니다.
	 */
	@Override
	public void update(@NotNull AnActionEvent e) {
		// 1. 현재 프로젝트 가져오기
		Project project = e.getData(CommonDataKeys.PROJECT);
		// 2. 현재 선택된 PSI 요소 가져오기
		PsiElement psiElement = e.getData(CommonDataKeys.PSI_ELEMENT);

		// 3. 프로젝트가 있고, 선택된 요소가 있으며, 그 요소가 Java 클래스이거나 클래스 내부에 있을 때만 액션 활성화
		boolean isJavaClass = false;
		if (project != null && psiElement != null) {
			PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
			if (psiFile instanceof com.intellij.psi.PsiJavaFile) {
				com.intellij.psi.PsiJavaFile javaFile = (com.intellij.psi.PsiJavaFile)psiFile;
				PsiClass[] classesInFile = javaFile.getClasses();
				if (classesInFile.length > 0) {
					// 파일에 클래스가 하나 이상 있다면 (보통 최상위 클래스)
					// 어떤 클래스를 소스로 할지는 추가 로직 필요 (예: 첫 번째 클래스)
					// 여기서는 단순히 파일에 Java 클래스가 있다는 것만 확인
					isJavaClass = true;
				}
			}
		}

		// 액션의 표시 상태 및 활성화 여부 설정
		e.getPresentation().setEnabledAndVisible(isJavaClass);
	}

	/**
	 * 액션이 실행될 때의 로직입니다.
	 * 선택된 Java 클래스를 소스 클래스로 플러그인 상태에 저장합니다.
	 */
	@Override
	public void actionPerformed(@NotNull AnActionEvent e) {
		// update 메소드에서 이미 null 체크를 했지만, 안전을 위해 다시 체크합니다.
		Project project = e.getData(CommonDataKeys.PROJECT);
		// PsiElement psiElement = e.getData(CommonDataKeys.PSI_ELEMENT);
		if (project == null) {
			return;
		}

		// 선택된 요소로부터 가장 가까운 부모 Java 클래스를 찾습니다.
		// **actionPerformed 시작 부분에서 PsiFile을 통해 PsiClass 가져오기**
		PsiClass sourceClass = null;
		PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
		if (psiFile instanceof PsiJavaFile) {
			PsiJavaFile javaFile = (PsiJavaFile)psiFile;
			PsiClass[] classesInFile = javaFile.getClasses();
			if (classesInFile.length > 0) {
				// 파일 내에 클래스가 하나 이상 있다면, 첫 번째 클래스를 소스 클래스로 사용
				// (일반적으로 Java 파일에는 하나의 최상위 public 클래스가 있습니다)
				sourceClass = classesInFile[0];
			}
		}

		// 1. 소스 클래스 정보 저장 (플러그인 상태 컴포넌트 사용)
		// MappingPluginState는 PersistentStateComponent를 상속받아 구현해야 합니다.
		// getInstance 메소드는 MappingPluginState에 static으로 구현되어 있어야 합니다.
		MappingPluginState state = MappingPluginState.getInstance(project);
		if (state != null) {
			String qualifiedName = sourceClass.getQualifiedName();
			state.setSourceClassQualifiedName(qualifiedName); // 클래스 정규화된 이름 저장

			// 2. 사용자에게 알림
			NotificationGroupManager.getInstance().getNotificationGroup("Mapping Plugin Notifications")
				.createNotification("Source class loaded", "Loaded: " + qualifiedName, NotificationType.INFORMATION)
				.notify(project);

			// 3. 플러그인 UI 업데이트 (Tool Window)
			ToolWindow toolWindow = ToolWindowManager.getInstance(project)
				.getToolWindow("MappingToolWindow"); // plugin.xml에 등록한 ID 사용
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

		} else {
			NotificationGroupManager.getInstance().getNotificationGroup("Mapping Plugin Notifications")
				.createNotification("Mapping generation failed", "Plugin state component not found.",
					NotificationType.ERROR)
				.notify(project);
		}
	}
}
