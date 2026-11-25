package com.coliving.platform.client.fake;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

/**
 * gRPC 연동 전 테스트용 — 실제 로그인 회원을 가짜 데이터에 추가
 * src/main/resources/fake-users.yaml 에 적은 회원을 FakeUserClient·FakeContractClient 가 함께 사용
 * (화면에서 실제로 로그인한 사람의 회원 ID 를 적어야 커뮤니티에서 그 사람을 알아봄)
 * TODO: gRPC 클라이언트로 바꾸면 이 클래스와 fake-users.yaml 삭제
 */
@Slf4j
@Component
public class FakeUsersLoader {

	private final List<FakeUser> users;

	public FakeUsersLoader() {
		this.users = load();
		if (!users.isEmpty()) {
			log.info("fake-users.yaml 에서 테스트 회원 {}명을 불러왔어요: {}", users.size(),
					users.stream().map(user -> user.id() + " " + user.name()).toList());
		}
	}

	public List<FakeUser> users() {
		return users;
	}

	@SuppressWarnings("unchecked")
	private static List<FakeUser> load() {
		ClassPathResource resource = new ClassPathResource("fake-users.yaml");
		if (!resource.exists()) {
			return List.of();
		}
		try (InputStream in = resource.getInputStream()) {
			Object root = new Yaml().load(in);
			if (!(root instanceof Map<?, ?> map) || !(map.get("users") instanceof List<?> list)) {
				return List.of();
			}
			List<FakeUser> result = new ArrayList<>();
			for (Object item : list) {
				Map<String, Object> user = (Map<String, Object>) item;
				result.add(new FakeUser(
						((Number) user.get("id")).longValue(),
						String.valueOf(user.getOrDefault("name", "테스트 회원")),
						user.get("roles") instanceof List<?> roles ? roles.stream().map(String::valueOf).toList() : List.of("USER"),
						user.get("buildingId") instanceof Number building ? building.longValue() : null,
						user.get("roomNumber") != null ? String.valueOf(user.get("roomNumber")) : null));
			}
			return List.copyOf(result);
		} catch (Exception e) {
			log.warn("fake-users.yaml 을 읽지 못했어요 — 형식을 확인해 주세요", e);
			return List.of();
		}
	}

	/** buildingId·roomNumber 는 입주자(또는 계약 중)일 때만 */
	public record FakeUser(Long id, String name, List<String> roles, Long buildingId, String roomNumber) {
	}
}
