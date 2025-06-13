package com.maker.ui;

import org.jetbrains.annotations.NotNull;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;

/**
 * Mapping Plugin Tool Window의 UI 내용을 생성하는 팩토리 클래스입니다.
 */
public class MappingToolWindowFactory implements ToolWindowFactory {

	/**
	 * Tool Window의 UI 내용을 생성하고 Tool Window에 추가합니다.
	 */
	@Override
	public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
		// 1. Tool Window의 실제 UI 컴포넌트 생성
		// MappingToolWindowContentPanel 생성자에서 이미 모든 UI 요소 (버튼 포함)와 레이아웃을 설정합니다.
		MappingToolWindowContentPanel contentPanel = new MappingToolWindowContentPanel(project);

		// 2. UI 컴포넌트를 Content로 감싸기
		ContentFactory contentFactory = ContentFactory.getInstance();
		Content content = contentFactory.createContent(contentPanel, "", false); // contentPanel을 content로 감쌈

		// 3. 생성된 Content를 Tool Window에 추가
		toolWindow.getContentManager().addContent(content);
	}

	// is
	@Override
	public boolean shouldBeAvailable(@NotNull Project project) {
		// Tool Window가 특정 프로젝트에서 사용 가능해야 하는 조건을 설정합니다.
		// 여기서는 항상 사용 가능하도록 true 반환 (필요에 따라 로직 변경)
		return true;
	}
}
