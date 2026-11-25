# community-service 연동 명세 (gRPC · Kafka)

> 작성: 오시문 · community-service가 **다른 서비스에서 받아오는** 정보(gRPC)와 **수신할** 이벤트(Kafka)
> 상태: **v2.0 최종** — user · contract · property gRPC 연동 완료 (2026-11-04)
> 이전 문서 「community-service 연동 요청 API」(v1.2)와 「community-service gRPC 초안」을 이 문서로 합침

---

## 0. 요약

### 0-1. gRPC (동기 조회) — 연동 완료

| # | 서버 (담당) | gRPC 포트 | package · service | 메서드 | 서버 proto | community proto |
|---|---|---|---|---|---|---|
| 1-1 | user-service (조휘재) | 9082 | `coliving.user.v1` · `UserService` | `GetUsers` | `user-service/src/main/proto/user_community.proto` | `user_community.proto` |
| 1-2 | user-service (조휘재) | 9082 | `coliving.user.v1` · `UserService` | `SearchUsers` | 〃 | 〃 |
| 2-1 | contract-service (한성현) | 9089 | `coliving.contract.v1` · `ContractResidentService` | `GetResidentRooms` | `contract-service/src/main/proto/contract_resident.proto` | `contract_community.proto` |
| 3-1 | property-service (김정호) | 9083 | `coliving.property.v1` · `BuildingService` | `GetBuildings` | `property-service/src/main/proto/building_service.proto` | `property_community.proto` |

| 사용처 | 메서드 |
|---|---|
| 게시글·댓글·채팅·쪽지함·차단·신고·제재 목록의 이름·프로필, 입주자·매니저 판단(`roles`) | 1-1 GetUsers |
| 관리자 쪽지 쓰기 — 받는 사람 검색 | 1-2 SearchUsers |
| 게시판·채팅 건물 범위(권한), 작성자·채팅 상대·신고 대상자 호실, 쪽지함 상세 건물·호실 | 2-1 GetResidentRooms |
| 관리자 게시판 건물 선택(5-10), FAQ 문서 적용 건물·미리보기 건물, 건물 이름 표시 | 3-1 GetBuildings |

### 0-2. Kafka (비동기 수신) — 예정

| # | 발행 → 수신 | 토픽 | 사용처 | 상태 |
|---|---|---|---|---|
| 4-1 | contract → community | `contract.completed` | 쪽지함 유형 후보자 → 입주자 | 미구현 |
| 4-2 | user → community | `resident.status-changed` | 퇴거 시 채팅방 읽기 전용 | 미구현 |
| 4-3 | user → community | `user.withdrawn` | 탈퇴 회원 작성자 표시·차단 정리 | 미구현 |

---

## 공통 규칙

### proto 작성 규칙 (팀)

| 항목 | 규칙 |
|---|---|
| 서비스 이름·package | **서버 쪽 이름 기준** (`coliving.{서비스}.v1`) |
| 일치해야 하는 것 | package · service 이름 · 메서드 이름 · 필드 번호와 타입 — 하나라도 다르면 `UNIMPLEMENTED` 또는 값이 비어서 옴 |
| 파일 위치 | 각 서비스 `src/main/proto/` (서버·클라이언트가 각자 복사본 보관) |
| 클라이언트 파일 | 자기가 쓰는 메서드만 둬도 됨 (서버에 다른 메서드가 추가돼도 영향 없음). community는 `{서버}_community.proto` |
| 필드 변경 | 번호는 바꾸거나 재사용하지 않음, 지울 때는 `reserved`, 추가는 새 번호로 (기존 클라이언트는 모르는 필드를 무시) |
| 유형 | 모두 Unary (요청 1번 → 응답 1번) |

### community 클라이언트 동작

| 항목 | 내용 |
|---|---|
| 라이브러리 | `spring-boot-starter-grpc-client` + `com.google.protobuf` 0.9.6 (버전은 Spring Boot가 관리, contract-service와 같은 방식) |
| stub 등록 | `@ImportGrpcClients(target = "user" / "contract" / "property", types = ...BlockingStub.class)` |
| 일괄 조회 | ID는 중복 제거 후 **100개씩** 나눠 호출 (서버 제한 100개) |
| 제한 시간 | 호출마다 **3초** (`withDeadlineAfter`) |
| 없는 ID | 서버는 에러 없이 결과에서 뺌 → community가 "알 수 없는 사용자" / "입주자 아님"으로 처리 |
| 서버 오류·꺼짐 | **503 ProblemDetail** `code: SERVICE_UNAVAILABLE` ("회원/입주/건물 정보를 불러오지 못했어요…") |
| 건물 목록 캐시 | GetBuildings는 30초 메모리 캐시, property-service가 잠깐 꺼지면 마지막 목록 사용 (한 번도 못 받았으면 503) |
| 가짜 전환 | `community.clients.{user,contract,property}` = `grpc` / `fake` — 그 서비스 없이 실행할 때만 `fake` (설정이 없으면 `fake`) |

### 설정 (config-server `community-service-dev.yaml`)

```yaml
spring:
  grpc:
    client:
      channel:
        user:
          target: localhost:9082     # user-service
        contract:
          target: localhost:9089     # contract-service
        property:
          target: localhost:9083     # property-service

community:
  clients:
    user: grpc
    contract: grpc
    property: grpc
```

> community-service를 켤 때 user · contract · property 서비스도 켜져 있어야 함. 하나를 끄고 테스트하려면 그 항목만 `fake`

---

## 1. 회원 — user-service

### 1-1. GetUsers — 회원 일괄 조회

| 항목 | 내용 |
|---|---|
| 메서드 | `UserService.GetUsers` · Unary |
| 호출 시점 | 게시글 목록·상세, 채팅방 목록, 쪽지함, 차단·신고·제재 목록 응답 직전 / 모든 커뮤니티 요청의 권한 확인 |

| 요청 `GetUsersRequest` | 타입 | 설명 |
|---|---|---|
| user_ids | repeated int64 | 회원 ID (최대 100개) |

| 응답 `GetUsersResponse.users[]` (`UserSummary`) | 타입 | 설명 |
|---|---|---|
| user_id | int64 | 회원 ID |
| name | string | 이름 |
| profile_image_url | optional string | 프로필 이미지 (없으면 비움) |
| roles | repeated string | `USER` / `CANDIDATE` / `RESIDENT` / `ADMIN` 중 여러 개 |

| 역할 | community 판단 |
|---|---|
| `RESIDENT` | 실제 입주일 ~ 퇴거일 동안만 부여 → 게시판·채팅 사용 가능 (건물은 2-1로 확인) |
| `ADMIN` | 매니저 — 공지 작성, 관리자 화면, 신고·차단·채팅 대상 아님 |
| `CANDIDATE` · `USER`만 | 커뮤니티 게시판·채팅 불가 (쪽지·FAQ 봇은 가능) |

### 1-2. SearchUsers — 회원 이름 검색 (관리자)

| 항목 | 내용 |
|---|---|
| 메서드 | `UserService.SearchUsers` · Unary |
| 호출 시점 | 관리자 쪽지함 → 새 쪽지 쓰기에서 받는 사람 검색 |

| 요청 `SearchUsersRequest` | 타입 | 설명 |
|---|---|---|
| keyword | string | 이름 검색어 (2자 이상 — community는 2자 미만이면 호출하지 않음) |
| role | optional string | 역할 필터 (`CANDIDATE` / `RESIDENT` / `USER`), 없으면 전체 |
| size | int32 | 최대 결과 수 (community는 10, 0이면 서버 기본 10) |

| 응답 | 설명 |
|---|---|
| `SearchUsersResponse.users[]` | `UserSummary` (1-1과 같음). 관리자는 community에서 제외, 건물·호실은 2-1로 채움 |

### 1-3. 관리자 담당 건물 — 보류

지금은 관리자 1명 = 전체 건물. 매니저가 여러 명이 되면 `UserService`에 메서드를 추가하는 방식으로 진행
(총관리자 `allBuildings = true`, 지점 매니저 `allBuildings = false` + `buildingIds`)

---

## 2. 입주 건물·호실 — contract-service

### 2-1. GetResidentRooms — 입주 건물·호실 일괄 조회

| 항목 | 내용 |
|---|---|
| 메서드 | `ContractResidentService.GetResidentRooms` · Unary |
| 호출 시점 | ① 커뮤니티·채팅 요청마다 본인 입주 건물 확인 ② 작성자·채팅 상대 호실 표시 ③ 쪽지함·신고 대상자 호실 |
| community 호출 조건 | `RESIDENT` 역할이 있는 회원만 (후보자는 계약이 있어도 커뮤니티 입주자 아님) |

| 요청 `GetResidentRoomsRequest` | 타입 | 설명 |
|---|---|---|
| user_ids | repeated int64 | 회원 ID (최대 100개, 넘으면 `INVALID_ARGUMENT`) |

| 응답 `GetResidentRoomsResponse.rooms[]` (`ResidentRoom`) | 타입 | 설명 | community 사용 |
|---|---|---|---|
| user_id | int64 | 회원 ID | |
| contract_id | int64 | `contract_application.id` | 보관 |
| building_id | int64 | 건물 ID | 게시글·채팅 건물 범위 |
| building_name | string | 건물명 | 화면 표시 |
| room_id | int64 | 호실 ID (후보자는 신청 호실) | 사용 안 함 |
| contract_status | string | `ContractStatus` 이름 그대로 | 보관 |
| move_in_date | optional string | `yyyy-MM-dd` 입주일 | 보관 |
| move_out_date | optional string | `yyyy-MM-dd` 퇴실 예정일 (이 날까지 거주) | 계약 종료일 |
| room_name | string | `"302호"` | **호실 표시** |

**회원당 1건 고르는 기준 (contract-service `ResidentRoomService`)**

| 순서 | 기준 |
|---|---|
| 1 | 상태 묶음: 입주 중·예정(`PAID`, `COMPLETED`, `MOVE_IN_CANCEL_REQUESTED`, `TERMINATION_REQUESTED`) → 심사·결제 대기(`SUBMITTED`, `APPROVED`) → 퇴실 확정(`TERMINATED`) |
| 2 | 같은 묶음이면 오늘이 입주일 ~ 퇴실 예정일 안에 있는 계약 |
| 3 | 입주일이 늦은 것 → id가 큰 것 |
| 제외 | `DRAFT`, `REJECTED`, `CANCELED`, `EXPIRED`, `REFUNDED` → 결과에서 빠짐 |

---

## 3. 건물 목록 — property-service

### 3-1. GetBuildings — 건물 목록 (간단)

| 항목 | 내용 |
|---|---|
| 메서드 | `BuildingService.GetBuildings` · Unary |
| 호출 시점 | 관리자 게시판 건물 선택(5-10), FAQ 문서 적용 범위·미리보기 건물, 건물 이름 표시·존재 확인 |
| 캐시 | community 30초 메모리 캐시 |

| 요청 `GetBuildingsRequest` | 설명 |
|---|---|
| (없음) | |

| 응답 `GetBuildingsResponse.buildings[]` (`BuildingSummary`) | 타입 | 설명 |
|---|---|---|
| building_id | int64 | 건물 ID |
| building_name | string | `buildings.building_name` |

---

## 4. Kafka 이벤트 (community-service 수신) — 예정, 미구현

> 토픽 이름·공통 필드(`eventId`, `occurredAt`)는 팀 규칙 확정 후 맞춤. 메시지 스키마는 팀 규칙에 따라 proto 메시지로 정의 예정

### 4-1. 계약 완료 `contract.completed`

| 항목 | 내용 |
|---|---|
| 발행 | contract-service (결제 완료로 계약 확정 시) |
| community 처리 | 해당 회원 쪽지함 `thread_type` → `RESIDENT`, `building_id` 갱신 |
| 필드 | `eventId`, `occurredAt`, `userId`, `contractId`, `buildingId`, `roomName`, `moveInDate`, `moveOutDate` |

### 4-2. 입주 상태 변경 `resident.status-changed`

| 항목 | 내용 |
|---|---|
| 발행 | user-service — 실제 입주로 `RESIDENT` 부여(`MOVED_IN`), 퇴거로 해제(`MOVED_OUT`) |
| community 처리 | `MOVED_OUT`이면 참여한 채팅방 `read_only = TRUE` (커뮤니티 접근은 `roles`로 바로 막힘, 신고·제재 기록은 보관) |
| 필드 | `eventId`, `occurredAt`, `userId`, `buildingId`, `type` (`MOVED_IN` / `MOVED_OUT`) |

### 4-3. 회원 탈퇴 `user.withdrawn`

| 항목 | 내용 |
|---|---|
| 발행 | user-service |
| community 처리 | 게시글·댓글 작성자 "탈퇴한 회원", 참여 채팅방 읽기 전용, 쪽지함 보관, 관련 차단 삭제, 진행 중 제재 종료 |
| 필드 | `eventId`, `occurredAt`, `userId` |

---

## 5. 남은 확인 사항

| # | 대상 | 내용 |
|---|---|---|
| 1 | 전체 | Kafka 토픽 이름 규칙, 공통 필드 통일, 메시지 proto 위치 |
| 2 | 조휘재 | 매니저가 여러 명이 될 때 담당 건물 조회 방식 (1-3) |
| 3 | 조휘재 | (참고) FAQ 봇 문서 업로드 최대 30MB — Gateway 업로드 크기 제한 확인 |

---

## 부록. community proto 파일 (`services/community-service/src/main/proto/`)

### user_community.proto
```proto
// ============================================================
// 유저 서비스 ↔ 커뮤니티 서비스 gRPC 계약서
// 서버: user-service (9082) / 클라이언트: community-service
// ⚠ 두 서비스가 똑같은 내용으로 가지고 있어야 함. 필드 번호는 바꾸거나 재사용하지 않고, 지울 때는 reserved
// ============================================================
syntax = "proto3";

package coliving.user.v1;

option java_multiple_files = true;
option java_package = "com.coliving.platform.grpc.user";
option java_outer_classname = "UserCommunityProto";

service UserService {
  // 1-1 회원 일괄 조회 (게시글·채팅·쪽지함·신고 목록의 이름·프로필·역할)
  // 없는 ID는 에러 없이 결과에서 빠짐 → community가 "알 수 없는 사용자"로 표시
  rpc GetUsers (GetUsersRequest) returns (GetUsersResponse);

  // 1-2 관리자 쪽지 쓰기 — 받는 사람 이름 검색
  rpc SearchUsers (SearchUsersRequest) returns (SearchUsersResponse);
}

message GetUsersRequest {
  repeated int64 user_ids = 1;          // 최대 100개
}

message GetUsersResponse {
  repeated UserSummary users = 1;
}

message UserSummary {
  int64 user_id = 1;
  string name = 2;
  optional string profile_image_url = 3; // 없으면 비워 둠
  repeated string roles = 4;             // USER / CANDIDATE / RESIDENT / ADMIN 중 여러 개
                                         // RESIDENT 는 실제 입주일 ~ 퇴거일 동안만 (커뮤니티 사용 가능 여부)
}

message SearchUsersRequest {
  string keyword = 1;                    // 이름 검색어 (2자 이상)
  optional string role = 2;              // 역할 필터 (CANDIDATE / RESIDENT / USER), 없으면 전체
  int32 size = 3;                        // 최대 결과 수 (0이면 서버 기본 10)
}

message SearchUsersResponse {
  repeated UserSummary users = 1;        // 건물·호실은 community가 contract 2-1로 채움
}
```

### contract_community.proto
```proto
// ============================================================
// 계약 서비스 ↔ 커뮤니티 서비스 gRPC 계약서
// 서버: contract-service (9089) / 클라이언트: community-service
// 원본: Confluence MFS「community-service grpc 초안」 3. contract_resident.proto
// ⚠ 두 서비스가 똑같은 내용으로 가지고 있어야 함. 필드 번호는 바꾸거나 재사용하지 않고, 지울 때는 reserved
//
// 초안에서 바꾼 것 (2026-11-03, community 담당과 맞춰야 함)
//   5  room_number(string "302호") → room_id(int64)    호실 ID
//   8  contract_end_date            → move_out_date     퇴실 예정일 (값은 그대로 계약 마지막 날)
//   9  room_name 추가                                   "302호" (작성자·채팅 상대 옆 호실 표시용)
// ============================================================
syntax = "proto3";

package coliving.contract.v1;

option java_multiple_files = true;
option java_package = "com.coliving.platform.grpc.contract";
option java_outer_classname = "ContractResidentProto";

service ContractResidentService {
  // 2-1 입주 건물·호실 일괄 조회
  // ① 커뮤니티·채팅 요청마다 본인 입주 건물 확인 ② 작성자·채팅 상대 호실 표시 ③ 쪽지함·신고 대상자 호실
  // 계약이 없는 회원은 에러 없이 결과에서 빠짐
  rpc GetResidentRooms (GetResidentRoomsRequest) returns (GetResidentRoomsResponse);
}

message GetResidentRoomsRequest {
  repeated int64 user_ids = 1;           // 최대 100개 (넘으면 INVALID_ARGUMENT)
}

message GetResidentRoomsResponse {
  repeated ResidentRoom rooms = 1;       // 회원당 1개 (현재 또는 가장 최근 계약)
}

message ResidentRoom {
  int64 user_id = 1;
  int64 contract_id = 2;                 // contract_application.id
  int64 building_id = 3;                 // 게시글·채팅 건물 범위 판단
  string building_name = 4;
  int64 room_id = 5;                     // 호실 ID (후보자는 신청한 호실)
  string contract_status = 6;            // contract-service ContractStatus 이름 그대로 (PAID, COMPLETED ...)
  optional string move_in_date = 7;      // "2025-06-12" (yyyy-MM-dd, 한국 날짜)
  optional string move_out_date = 8;     // "2026-06-11" 퇴실 예정일 (계약 마지막 날, 이 날까지 거주)
  string room_name = 9;                  // "302호"
}
```

### property_community.proto
```proto
// ============================================================
// 매물 서비스 ↔ 커뮤니티 서비스 gRPC 계약서
// 서버: property-service / 클라이언트: community-service
// ⚠ 두 서비스가 똑같은 내용으로 가지고 있어야 함. 필드 번호는 바꾸거나 재사용하지 않고, 지울 때는 reserved
// ============================================================
syntax = "proto3";

package coliving.property.v1;

option java_multiple_files = true;
option java_package = "com.coliving.platform.grpc.property";
option java_outer_classname = "BuildingProto";

service BuildingService {
  // 3-1 건물 목록 (간단) — FAQ 문서 범위·건물 선택, 게시판 건물 선택, 건물 이름 표시
  rpc GetBuildings (GetBuildingsRequest) returns (GetBuildingsResponse);
}

message GetBuildingsRequest {
}

message GetBuildingsResponse {
  repeated BuildingSummary buildings = 1;
}

message BuildingSummary {
  int64 building_id = 1;
  string building_name = 2;              // buildings.building_name
}
```
