package com.maker.state;

import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;

@State(
	name = "MappingPluginState", // 상태 파일 이름 (예: MappingPluginState.xml)
	storages = @Storage("mappingPluginState.xml") // 저장 파일 이름 및 위치 (프로젝트별 설정)
)
public class MappingPluginState implements PersistentStateComponent<MappingPluginState.State> {

	// 플러그인 상태를 담을 내부 클래스 (public 필드 또는 public Getter/Setter 사용)
	public static class State {
		public String sourceClassQualifiedName;
		public String targetClassQualifiedName;
		public List<String> includedTargetFieldNames; // Lock On에서 선택된 필드 이름 목록 (null 가능)
		public Boolean generateListMethod;
		public Boolean generateMethodComment;

		// 기본 생성자 필요
		public State() {
		}

		// 모든 필드를 받는 생성자 (선택 사항)
		public State(String sourceClassQName, String targetClassQName, List<String> includedFieldNames,
			Boolean generateListMethod, Boolean generateMethodComment) {
			this.sourceClassQualifiedName = sourceClassQName;
			this.targetClassQualifiedName = targetClassQName;
			this.includedTargetFieldNames = includedFieldNames;
			this.generateListMethod = generateListMethod;
			this.generateMethodComment = generateMethodComment;
		}
	}

	private State myState = new State(); // 현재 상태 객체

	@Override
	public State getState() {
		return myState; // 현재 상태 반환
	}

	@Override
	public void loadState(@NotNull State state) {
		// 저장된 상태를 불러와 현재 상태에 적용
		myState = state;
	}

	// 상태 필드에 접근하기 위한 Getter/Setter
	public String getSourceClassQualifiedName() {
		return myState.sourceClassQualifiedName;
	}

	public void setSourceClassQualifiedName(String sourceClassQualifiedName) {
		myState.sourceClassQualifiedName = sourceClassQualifiedName;
	}

	public String getTargetClassQualifiedName() {
		return myState.targetClassQualifiedName;
	}

	public void setTargetClassQualifiedName(String targetClassQualifiedName) {
		myState.targetClassQualifiedName = targetClassQualifiedName;
	}

	public List<String> getIncludedTargetFieldNames() {
		return myState.includedTargetFieldNames;
	}

	public void setIncludedTargetFieldNames(List<String> includedTargetFieldNames) {
		myState.includedTargetFieldNames = includedTargetFieldNames;
	}

	public Boolean isGenerateListMethod() {
		return myState.generateListMethod;
	} // Boolean 타입은 is로 시작하는 Getter가 일반적

	public void setGenerateListMethod(Boolean generateListMethod) {
		myState.generateListMethod = generateListMethod;
	}

	public Boolean isGenerateMethodComment() {
		return myState.generateMethodComment;
	}

	public void setGenerateMethodComment(Boolean generateMethodComment) {
		myState.generateMethodComment = generateMethodComment;
	}

	// 플러그인 서비스 인스턴스를 얻기 위한 static 헬퍼 메소드
	// IntelliJ 버전별로 ServiceManager 또는 PluginManagerCore 사용
	public static MappingPluginState getInstance(Project project) {
		// 최신 버전 (2019.2 이상)
		return project.getService(MappingPluginState.class);

		// 구 버전 (2019.2 미만)
		// return ServiceManager.getService(project, MappingPluginState.class);
	}
}
