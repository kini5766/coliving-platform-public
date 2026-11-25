# dashboard-service Elasticsearch 설정

대시보드 서비스는 Elasticsearch(ES)의 세 인덱스 `dashboard-snapshot`, `activity-log`, `room-status`에 데이터를 저장합니다.
서비스가 인덱스를 만들어 주지 않으므로(`createIndex = false`), **처음 실행하기 전에 아래 순서로 직접 만들어야 합니다. 인덱스를 만들지 않고 실행하면 조회는 에러가 나고, 저장은 ES가 필드 타입을 추측해 인덱스를 자동으로 만들어서 매핑이 어긋납니다.**

## 1. ES와 Kibana 실행

프로젝트 루트에서 실행합니다.

```
docker compose up -d elasticsearch kibana
```

`http://localhost:9200`에 JSON이 나오면 ES가 정상이고, `http://localhost:5601`에서 Kibana가 열리면 됩니다 (처음엔 1~2분 걸립니다).
이후 명령은 Kibana Dev Tools(**http://localhost:5601/app/dev_tools#/console**)에 붙여 넣고 ▶로 실행합니다.

## 2. 인덱스 만들기

세 개를 각각 실행합니다. 응답에 `"acknowledged": true`가 나오면 성공입니다.

```
PUT dashboard-snapshot
{
  "settings": { "number_of_shards": 1, "number_of_replicas": 0 },
  "mappings": {
    "properties": {
      "building_id":               { "type": "long" },
      "building_name":             { "type": "keyword" },
      "snapshot_date":             { "type": "date", "format": "yyyy-MM-dd" },
      "total_rooms":               { "type": "integer" },
      "occupied_rooms":            { "type": "integer" },
      "occupancy_rate":            { "type": "scaled_float", "scaling_factor": 100 },
      "resident_count":            { "type": "integer" },
      "moving_out_count":          { "type": "integer" },
      "move_in_scheduled_count":   { "type": "integer" },
      "pending_approval_count":    { "type": "integer" },
      "pending_reservation_count": { "type": "integer" },
      "month_collected_amount":    { "type": "long" },
      "deposit_held_amount":       { "type": "long" },
      "month_refunded_amount":     { "type": "long" },
      "month_refunded_count":      { "type": "integer" },
      "applied_event_keys":        { "type": "keyword" },
      "created_at":                { "type": "date" },
      "updated_at":                { "type": "date" }
    }
  }
}
```

```
PUT activity-log
{
  "settings": { "number_of_shards": 1, "number_of_replicas": 0 },
  "mappings": {
    "properties": {
      "event_id":       { "type": "keyword" },
      "building_id":    { "type": "long" },
      "building_name":  { "type": "keyword" },
      "activity_type":  { "type": "keyword" },
      "content":        { "type": "text", "fields": { "keyword": { "type": "keyword", "ignore_above": 512 } } },
      "status":         { "type": "keyword" },
      "source_service": { "type": "keyword" },
      "source_ref_id":  { "type": "keyword" },
      "occurred_at":    { "type": "date" },
      "updated_at":     { "type": "date" }
    }
  }
}
```

```
PUT room-status
{
  "settings": { "number_of_shards": 1, "number_of_replicas": 0 },
  "mappings": {
    "properties": {
      "room_id":        { "type": "long" },
      "building_id":    { "type": "long" },
      "building_name":  { "type": "keyword" },
      "floor":          { "type": "integer" },
      "room_name":      { "type": "keyword" },
      "status":         { "type": "keyword" },
      "application_id": { "type": "long" },
      "move_in_date":   { "type": "date", "format": "yyyy-MM-dd" },
      "move_out_date":  { "type": "date", "format": "yyyy-MM-dd" },
      "updated_at":     { "type": "date" }
    }
  }
}
```

### 이미 만든 `dashboard-snapshot`에 `applied_event_keys` 추가하기

`applied_event_keys`는 결제·환불 이벤트가 다시 전달돼도 금액을 두 번 더하지 않도록, 당월에 반영한 결제·환불 키(`PAYMENT-{결제 ID}`, `REFUND-{결제 ID}`)를 저장하는 필드입니다.
이 필드가 없던 때 만든 인덱스는 **데이터를 지우지 않고** 아래 한 번만 실행하면 됩니다. 새로 만드는 인덱스는 위 `PUT dashboard-snapshot`에 이미 들어 있으므로 따로 할 일이 없습니다.

```
PUT dashboard-snapshot/_mapping
{
  "properties": {
    "applied_event_keys": { "type": "keyword" }
  }
}
```

이 매핑을 넣지 않아도 서비스는 동작하지만, ES가 필드를 `text`로 추측해서 만듭니다. 한번 만들어진 필드의 타입은 바꿀 수 없으므로, 결제·환불 이벤트가 들어오기 전에 먼저 실행하세요.

### 매핑을 바꿔야 할 때

이미 만든 인덱스의 매핑을 바꾸려면 인덱스를 지우고 위 `PUT`을 다시 실행합니다. **인덱스 안의 데이터가 모두 사라집니다.**

```
DELETE dashboard-snapshot
DELETE activity-log
DELETE room-status
```


## 3. 더미 데이터 (선택, 화면 확인용)

연동 전에 화면을 확인하기 위한 데이터입니다. 실제 서비스가 연결되면 지워도 됩니다.

- 건물 ID: **1 망원 스테이, 2 연남 하우스, 3 성수 로프트**
- 날짜·시간 값(`created_at`, `updated_at`, `occurred_at`)은 `2026-10-26T15:00:00.000Z`처럼 **밀리초(`.000`)까지** 써야 합니다.
- `_bulk`는 줄 하나가 데이터 하나이므로 **줄바꿈을 바꾸지 말고** 그대로 붙여 넣습니다. 응답에 `"errors": false`가 나오면 성공입니다.
- 호실 현황은 상태와 날짜로 계산됩니다. 입주 예정(`MOVE_IN_SCHEDULED`) 호실은 `move_in_date`가 되면 입주로, 입주 중·퇴거 예정 호실은 `move_out_date`가 지나면 공실로 보입니다. 이 데이터의 날짜는 2026-11-10 기준이라, 그보다 한참 뒤에 넣으면 날짜를 고쳐서 넣으세요.
- 스냅샷 더미 데이터에는 `applied_event_keys`가 없습니다. 서비스는 이 필드가 없으면 빈 목록으로 보므로 그대로 넣어도 됩니다.

### 3-1. 일별 스냅샷 (3개 건물 × 7건 = 21건)

```
POST _bulk
{"index":{"_index":"dashboard-snapshot","_id":"1_2026-05-31"}}
{"building_id":1,"building_name":"망원 스테이","snapshot_date":"2026-05-31","total_rooms":20,"occupied_rooms":17,"occupancy_rate":85.0,"resident_count":17,"moving_out_count":1,"move_in_scheduled_count":1,"pending_approval_count":2,"pending_reservation_count":0,"month_collected_amount":12480000,"deposit_held_amount":34000000,"month_refunded_amount":2000000,"month_refunded_count":1,"created_at":"2026-05-31T15:00:00.000Z","updated_at":"2026-05-31T15:00:00.000Z"}
{"index":{"_index":"dashboard-snapshot","_id":"1_2026-06-30"}}
{"building_id":1,"building_name":"망원 스테이","snapshot_date":"2026-06-30","total_rooms":20,"occupied_rooms":18,"occupancy_rate":90.0,"resident_count":18,"moving_out_count":1,"move_in_scheduled_count":0,"pending_approval_count":1,"pending_reservation_count":1,"month_collected_amount":14040000,"deposit_held_amount":36000000,"month_refunded_amount":2000000,"month_refunded_count":1,"created_at":"2026-06-30T15:00:00.000Z","updated_at":"2026-06-30T15:00:00.000Z"}
{"index":{"_index":"dashboard-snapshot","_id":"1_2026-07-31"}}
{"building_id":1,"building_name":"망원 스테이","snapshot_date":"2026-07-31","total_rooms":20,"occupied_rooms":16,"occupancy_rate":80.0,"resident_count":16,"moving_out_count":3,"move_in_scheduled_count":1,"pending_approval_count":2,"pending_reservation_count":0,"month_collected_amount":11700000,"deposit_held_amount":32000000,"month_refunded_amount":6000000,"month_refunded_count":3,"created_at":"2026-07-31T15:00:00.000Z","updated_at":"2026-07-31T15:00:00.000Z"}
{"index":{"_index":"dashboard-snapshot","_id":"1_2026-08-31"}}
{"building_id":1,"building_name":"망원 스테이","snapshot_date":"2026-08-31","total_rooms":20,"occupied_rooms":15,"occupancy_rate":75.0,"resident_count":15,"moving_out_count":1,"move_in_scheduled_count":2,"pending_approval_count":3,"pending_reservation_count":2,"month_collected_amount":10140000,"deposit_held_amount":30000000,"month_refunded_amount":2000000,"month_refunded_count":1,"created_at":"2026-08-31T15:00:00.000Z","updated_at":"2026-08-31T15:00:00.000Z"}
{"index":{"_index":"dashboard-snapshot","_id":"1_2026-09-30"}}
{"building_id":1,"building_name":"망원 스테이","snapshot_date":"2026-09-30","total_rooms":20,"occupied_rooms":16,"occupancy_rate":80.0,"resident_count":16,"moving_out_count":2,"move_in_scheduled_count":1,"pending_approval_count":2,"pending_reservation_count":1,"month_collected_amount":11700000,"deposit_held_amount":32000000,"month_refunded_amount":4000000,"month_refunded_count":2,"created_at":"2026-09-30T15:00:00.000Z","updated_at":"2026-09-30T15:00:00.000Z"}
{"index":{"_index":"dashboard-snapshot","_id":"1_2026-10-19"}}
{"building_id":1,"building_name":"망원 스테이","snapshot_date":"2026-10-19","total_rooms":20,"occupied_rooms":17,"occupancy_rate":85.0,"resident_count":17,"moving_out_count":2,"move_in_scheduled_count":1,"pending_approval_count":2,"pending_reservation_count":0,"month_collected_amount":9360000,"deposit_held_amount":34000000,"month_refunded_amount":4000000,"month_refunded_count":2,"created_at":"2026-10-19T15:00:00.000Z","updated_at":"2026-10-19T15:00:00.000Z"}
{"index":{"_index":"dashboard-snapshot","_id":"1_2026-10-26"}}
{"building_id":1,"building_name":"망원 스테이","snapshot_date":"2026-10-26","total_rooms":20,"occupied_rooms":17,"occupancy_rate":85.0,"resident_count":17,"moving_out_count":2,"move_in_scheduled_count":1,"pending_approval_count":1,"pending_reservation_count":1,"month_collected_amount":12480000,"deposit_held_amount":34000000,"month_refunded_amount":4000000,"month_refunded_count":2,"created_at":"2026-10-26T15:00:00.000Z","updated_at":"2026-10-26T15:00:00.000Z"}
{"index":{"_index":"dashboard-snapshot","_id":"2_2026-05-31"}}
{"building_id":2,"building_name":"연남 하우스","snapshot_date":"2026-05-31","total_rooms":15,"occupied_rooms":12,"occupancy_rate":80.0,"resident_count":12,"moving_out_count":1,"move_in_scheduled_count":0,"pending_approval_count":1,"pending_reservation_count":1,"month_collected_amount":8280000,"deposit_held_amount":18000000,"month_refunded_amount":1500000,"month_refunded_count":1,"created_at":"2026-05-31T15:00:00.000Z","updated_at":"2026-05-31T15:00:00.000Z"}
{"index":{"_index":"dashboard-snapshot","_id":"2_2026-06-30"}}
{"building_id":2,"building_name":"연남 하우스","snapshot_date":"2026-06-30","total_rooms":15,"occupied_rooms":11,"occupancy_rate":73.3,"resident_count":11,"moving_out_count":2,"move_in_scheduled_count":1,"pending_approval_count":2,"pending_reservation_count":0,"month_collected_amount":6900000,"deposit_held_amount":16500000,"month_refunded_amount":3000000,"month_refunded_count":2,"created_at":"2026-06-30T15:00:00.000Z","updated_at":"2026-06-30T15:00:00.000Z"}
{"index":{"_index":"dashboard-snapshot","_id":"2_2026-07-31"}}
{"building_id":2,"building_name":"연남 하우스","snapshot_date":"2026-07-31","total_rooms":15,"occupied_rooms":13,"occupancy_rate":86.7,"resident_count":13,"moving_out_count":0,"move_in_scheduled_count":0,"pending_approval_count":1,"pending_reservation_count":0,"month_collected_amount":8970000,"deposit_held_amount":19500000,"month_refunded_amount":0,"month_refunded_count":0,"created_at":"2026-07-31T15:00:00.000Z","updated_at":"2026-07-31T15:00:00.000Z"}
{"index":{"_index":"dashboard-snapshot","_id":"2_2026-08-31"}}
{"building_id":2,"building_name":"연남 하우스","snapshot_date":"2026-08-31","total_rooms":15,"occupied_rooms":14,"occupancy_rate":93.3,"resident_count":14,"moving_out_count":1,"move_in_scheduled_count":0,"pending_approval_count":0,"pending_reservation_count":1,"month_collected_amount":8970000,"deposit_held_amount":21000000,"month_refunded_amount":1500000,"month_refunded_count":1,"created_at":"2026-08-31T15:00:00.000Z","updated_at":"2026-08-31T15:00:00.000Z"}
{"index":{"_index":"dashboard-snapshot","_id":"2_2026-09-30"}}
{"building_id":2,"building_name":"연남 하우스","snapshot_date":"2026-09-30","total_rooms":15,"occupied_rooms":13,"occupancy_rate":86.7,"resident_count":13,"moving_out_count":2,"move_in_scheduled_count":1,"pending_approval_count":2,"pending_reservation_count":0,"month_collected_amount":7590000,"deposit_held_amount":19500000,"month_refunded_amount":3000000,"month_refunded_count":2,"created_at":"2026-09-30T15:00:00.000Z","updated_at":"2026-09-30T15:00:00.000Z"}
{"index":{"_index":"dashboard-snapshot","_id":"2_2026-10-19"}}
{"building_id":2,"building_name":"연남 하우스","snapshot_date":"2026-10-19","total_rooms":15,"occupied_rooms":14,"occupancy_rate":93.3,"resident_count":14,"moving_out_count":1,"move_in_scheduled_count":0,"pending_approval_count":1,"pending_reservation_count":1,"month_collected_amount":6210000,"deposit_held_amount":21000000,"month_refunded_amount":1500000,"month_refunded_count":1,"created_at":"2026-10-19T15:00:00.000Z","updated_at":"2026-10-19T15:00:00.000Z"}
{"index":{"_index":"dashboard-snapshot","_id":"2_2026-10-26"}}
{"building_id":2,"building_name":"연남 하우스","snapshot_date":"2026-10-26","total_rooms":15,"occupied_rooms":14,"occupancy_rate":93.3,"resident_count":14,"moving_out_count":1,"move_in_scheduled_count":0,"pending_approval_count":2,"pending_reservation_count":0,"month_collected_amount":8970000,"deposit_held_amount":21000000,"month_refunded_amount":1500000,"month_refunded_count":1,"created_at":"2026-10-26T15:00:00.000Z","updated_at":"2026-10-26T15:00:00.000Z"}
{"index":{"_index":"dashboard-snapshot","_id":"3_2026-05-31"}}
{"building_id":3,"building_name":"성수 로프트","snapshot_date":"2026-05-31","total_rooms":24,"occupied_rooms":17,"occupancy_rate":70.8,"resident_count":17,"moving_out_count":1,"move_in_scheduled_count":1,"pending_approval_count":3,"pending_reservation_count":0,"month_collected_amount":14720000,"deposit_held_amount":51000000,"month_refunded_amount":3000000,"month_refunded_count":1,"created_at":"2026-05-31T15:00:00.000Z","updated_at":"2026-05-31T15:00:00.000Z"}
{"index":{"_index":"dashboard-snapshot","_id":"3_2026-06-30"}}
{"building_id":3,"building_name":"성수 로프트","snapshot_date":"2026-06-30","total_rooms":24,"occupied_rooms":19,"occupancy_rate":79.2,"resident_count":19,"moving_out_count":2,"move_in_scheduled_count":2,"pending_approval_count":2,"pending_reservation_count":1,"month_collected_amount":15640000,"deposit_held_amount":57000000,"month_refunded_amount":6000000,"month_refunded_count":2,"created_at":"2026-06-30T15:00:00.000Z","updated_at":"2026-06-30T15:00:00.000Z"}
{"index":{"_index":"dashboard-snapshot","_id":"3_2026-07-31"}}
{"building_id":3,"building_name":"성수 로프트","snapshot_date":"2026-07-31","total_rooms":24,"occupied_rooms":21,"occupancy_rate":87.5,"resident_count":21,"moving_out_count":1,"move_in_scheduled_count":0,"pending_approval_count":3,"pending_reservation_count":0,"month_collected_amount":18400000,"deposit_held_amount":63000000,"month_refunded_amount":3000000,"month_refunded_count":1,"created_at":"2026-07-31T15:00:00.000Z","updated_at":"2026-07-31T15:00:00.000Z"}
{"index":{"_index":"dashboard-snapshot","_id":"3_2026-08-31"}}
{"building_id":3,"building_name":"성수 로프트","snapshot_date":"2026-08-31","total_rooms":24,"occupied_rooms":20,"occupancy_rate":83.3,"resident_count":20,"moving_out_count":3,"move_in_scheduled_count":1,"pending_approval_count":2,"pending_reservation_count":0,"month_collected_amount":16560000,"deposit_held_amount":60000000,"month_refunded_amount":9000000,"month_refunded_count":3,"created_at":"2026-08-31T15:00:00.000Z","updated_at":"2026-08-31T15:00:00.000Z"}
{"index":{"_index":"dashboard-snapshot","_id":"3_2026-09-30"}}
{"building_id":3,"building_name":"성수 로프트","snapshot_date":"2026-09-30","total_rooms":24,"occupied_rooms":19,"occupancy_rate":79.2,"resident_count":19,"moving_out_count":2,"move_in_scheduled_count":1,"pending_approval_count":1,"pending_reservation_count":1,"month_collected_amount":16560000,"deposit_held_amount":57000000,"month_refunded_amount":6000000,"month_refunded_count":2,"created_at":"2026-09-30T15:00:00.000Z","updated_at":"2026-09-30T15:00:00.000Z"}
{"index":{"_index":"dashboard-snapshot","_id":"3_2026-10-19"}}
{"building_id":3,"building_name":"성수 로프트","snapshot_date":"2026-10-19","total_rooms":24,"occupied_rooms":20,"occupancy_rate":83.3,"resident_count":20,"moving_out_count":2,"move_in_scheduled_count":0,"pending_approval_count":3,"pending_reservation_count":0,"month_collected_amount":14720000,"deposit_held_amount":60000000,"month_refunded_amount":6000000,"month_refunded_count":2,"created_at":"2026-10-19T15:00:00.000Z","updated_at":"2026-10-19T15:00:00.000Z"}
{"index":{"_index":"dashboard-snapshot","_id":"3_2026-10-26"}}
{"building_id":3,"building_name":"성수 로프트","snapshot_date":"2026-10-26","total_rooms":24,"occupied_rooms":21,"occupancy_rate":87.5,"resident_count":21,"moving_out_count":2,"move_in_scheduled_count":1,"pending_approval_count":2,"pending_reservation_count":0,"month_collected_amount":17480000,"deposit_held_amount":63000000,"month_refunded_amount":6000000,"month_refunded_count":2,"created_at":"2026-10-26T15:00:00.000Z","updated_at":"2026-10-26T15:00:00.000Z"}
```

### 3-2. 호실 현황 (20 + 15 + 24 = 59호실)

```
POST _bulk
{"index":{"_index":"room-status","_id":"1101"}}
{"room_id":1101,"building_id":1,"building_name":"망원 스테이","floor":1,"room_name":"101호","status":"OCCUPIED","application_id":801,"move_in_date":"2026-02-03","move_out_date":"2027-02-02","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"1102"}}
{"room_id":1102,"building_id":1,"building_name":"망원 스테이","floor":1,"room_name":"102호","status":"OCCUPIED","application_id":802,"move_in_date":"2026-03-10","move_out_date":"2027-03-09","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"1103"}}
{"room_id":1103,"building_id":1,"building_name":"망원 스테이","floor":1,"room_name":"103호","status":"OCCUPIED","application_id":803,"move_in_date":"2026-04-01","move_out_date":"2027-03-31","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"1104"}}
{"room_id":1104,"building_id":1,"building_name":"망원 스테이","floor":1,"room_name":"104호","status":"MOVING_OUT","application_id":804,"move_in_date":"2026-05-15","move_out_date":"2026-11-18","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"1105"}}
{"room_id":1105,"building_id":1,"building_name":"망원 스테이","floor":1,"room_name":"105호","status":"VACANT","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"1201"}}
{"room_id":1201,"building_id":1,"building_name":"망원 스테이","floor":2,"room_name":"201호","status":"OCCUPIED","application_id":805,"move_in_date":"2026-06-01","move_out_date":"2027-05-31","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"1202"}}
{"room_id":1202,"building_id":1,"building_name":"망원 스테이","floor":2,"room_name":"202호","status":"OCCUPIED","application_id":806,"move_in_date":"2026-07-01","move_out_date":"2027-06-30","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"1203"}}
{"room_id":1203,"building_id":1,"building_name":"망원 스테이","floor":2,"room_name":"203호","status":"OCCUPIED","application_id":807,"move_in_date":"2026-08-10","move_out_date":"2027-08-09","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"1204"}}
{"room_id":1204,"building_id":1,"building_name":"망원 스테이","floor":2,"room_name":"204호","status":"OCCUPIED","application_id":655,"move_in_date":"2026-10-27","move_out_date":"2027-04-26","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"1205"}}
{"room_id":1205,"building_id":1,"building_name":"망원 스테이","floor":2,"room_name":"205호","status":"OCCUPIED","application_id":808,"move_in_date":"2026-09-01","move_out_date":"2027-02-28","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"1301"}}
{"room_id":1301,"building_id":1,"building_name":"망원 스테이","floor":3,"room_name":"301호","status":"OCCUPIED","application_id":809,"move_in_date":"2026-10-01","move_out_date":"2027-09-30","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"1302"}}
{"room_id":1302,"building_id":1,"building_name":"망원 스테이","floor":3,"room_name":"302호","status":"APPLYING","application_id":810,"move_in_date":"2026-11-18","move_out_date":"2027-05-17","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"1303"}}
{"room_id":1303,"building_id":1,"building_name":"망원 스테이","floor":3,"room_name":"303호","status":"OCCUPIED","application_id":811,"move_in_date":"2026-02-03","move_out_date":"2027-02-02","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"1304"}}
{"room_id":1304,"building_id":1,"building_name":"망원 스테이","floor":3,"room_name":"304호","status":"OCCUPIED","application_id":812,"move_in_date":"2026-03-10","move_out_date":"2027-03-09","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"1305"}}
{"room_id":1305,"building_id":1,"building_name":"망원 스테이","floor":3,"room_name":"305호","status":"MOVE_IN_SCHEDULED","application_id":813,"move_in_date":"2026-11-20","move_out_date":"2027-05-19","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"1401"}}
{"room_id":1401,"building_id":1,"building_name":"망원 스테이","floor":4,"room_name":"401호","status":"OCCUPIED","application_id":814,"move_in_date":"2026-04-01","move_out_date":"2027-03-31","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"1402"}}
{"room_id":1402,"building_id":1,"building_name":"망원 스테이","floor":4,"room_name":"402호","status":"MOVING_OUT","application_id":662,"move_in_date":"2026-05-15","move_out_date":"2026-11-30","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"1403"}}
{"room_id":1403,"building_id":1,"building_name":"망원 스테이","floor":4,"room_name":"403호","status":"OCCUPIED","application_id":815,"move_in_date":"2026-06-01","move_out_date":"2027-05-31","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"1404"}}
{"room_id":1404,"building_id":1,"building_name":"망원 스테이","floor":4,"room_name":"404호","status":"OCCUPIED","application_id":816,"move_in_date":"2026-07-01","move_out_date":"2027-06-30","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"1405"}}
{"room_id":1405,"building_id":1,"building_name":"망원 스테이","floor":4,"room_name":"405호","status":"OCCUPIED","application_id":817,"move_in_date":"2026-08-10","move_out_date":"2027-08-09","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"2101"}}
{"room_id":2101,"building_id":2,"building_name":"연남 하우스","floor":1,"room_name":"101호","status":"OCCUPIED","application_id":818,"move_in_date":"2026-09-01","move_out_date":"2027-02-28","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"2102"}}
{"room_id":2102,"building_id":2,"building_name":"연남 하우스","floor":1,"room_name":"102호","status":"OCCUPIED","application_id":819,"move_in_date":"2026-10-01","move_out_date":"2027-09-30","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"2103"}}
{"room_id":2103,"building_id":2,"building_name":"연남 하우스","floor":1,"room_name":"103호","status":"OCCUPIED","application_id":820,"move_in_date":"2026-02-03","move_out_date":"2027-02-02","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"2104"}}
{"room_id":2104,"building_id":2,"building_name":"연남 하우스","floor":1,"room_name":"104호","status":"OCCUPIED","application_id":821,"move_in_date":"2026-03-10","move_out_date":"2027-03-09","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"2105"}}
{"room_id":2105,"building_id":2,"building_name":"연남 하우스","floor":1,"room_name":"105호","status":"MOVING_OUT","application_id":705,"move_in_date":"2026-04-01","move_out_date":"2026-12-05","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"2201"}}
{"room_id":2201,"building_id":2,"building_name":"연남 하우스","floor":2,"room_name":"201호","status":"OCCUPIED","application_id":822,"move_in_date":"2026-05-15","move_out_date":"2027-05-14","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"2202"}}
{"room_id":2202,"building_id":2,"building_name":"연남 하우스","floor":2,"room_name":"202호","status":"OCCUPIED","application_id":823,"move_in_date":"2026-06-01","move_out_date":"2027-05-31","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"2203"}}
{"room_id":2203,"building_id":2,"building_name":"연남 하우스","floor":2,"room_name":"203호","status":"APPLYING","application_id":720,"move_in_date":"2026-11-15","move_out_date":"2027-05-14","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"2204"}}
{"room_id":2204,"building_id":2,"building_name":"연남 하우스","floor":2,"room_name":"204호","status":"OCCUPIED","application_id":824,"move_in_date":"2026-07-01","move_out_date":"2027-06-30","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"2205"}}
{"room_id":2205,"building_id":2,"building_name":"연남 하우스","floor":2,"room_name":"205호","status":"OCCUPIED","application_id":825,"move_in_date":"2026-08-10","move_out_date":"2027-08-09","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"2301"}}
{"room_id":2301,"building_id":2,"building_name":"연남 하우스","floor":3,"room_name":"301호","status":"OCCUPIED","application_id":826,"move_in_date":"2026-09-01","move_out_date":"2027-02-28","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"2302"}}
{"room_id":2302,"building_id":2,"building_name":"연남 하우스","floor":3,"room_name":"302호","status":"OCCUPIED","application_id":827,"move_in_date":"2026-10-01","move_out_date":"2027-09-30","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"2303"}}
{"room_id":2303,"building_id":2,"building_name":"연남 하우스","floor":3,"room_name":"303호","status":"OCCUPIED","application_id":828,"move_in_date":"2026-02-03","move_out_date":"2027-02-02","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"2304"}}
{"room_id":2304,"building_id":2,"building_name":"연남 하우스","floor":3,"room_name":"304호","status":"OCCUPIED","application_id":829,"move_in_date":"2026-03-10","move_out_date":"2027-03-09","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"2305"}}
{"room_id":2305,"building_id":2,"building_name":"연남 하우스","floor":3,"room_name":"305호","status":"OCCUPIED","application_id":830,"move_in_date":"2026-04-01","move_out_date":"2027-03-31","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"3101"}}
{"room_id":3101,"building_id":3,"building_name":"성수 로프트","floor":1,"room_name":"101호","status":"OCCUPIED","application_id":831,"move_in_date":"2026-05-15","move_out_date":"2027-05-14","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"3102"}}
{"room_id":3102,"building_id":3,"building_name":"성수 로프트","floor":1,"room_name":"102호","status":"OCCUPIED","application_id":832,"move_in_date":"2026-06-01","move_out_date":"2027-05-31","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"3103"}}
{"room_id":3103,"building_id":3,"building_name":"성수 로프트","floor":1,"room_name":"103호","status":"OCCUPIED","application_id":833,"move_in_date":"2026-07-01","move_out_date":"2027-06-30","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"3104"}}
{"room_id":3104,"building_id":3,"building_name":"성수 로프트","floor":1,"room_name":"104호","status":"OCCUPIED","application_id":834,"move_in_date":"2026-08-10","move_out_date":"2027-02-09","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"3105"}}
{"room_id":3105,"building_id":3,"building_name":"성수 로프트","floor":1,"room_name":"105호","status":"MOVING_OUT","application_id":835,"move_in_date":"2026-09-01","move_out_date":"2026-11-22","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"3106"}}
{"room_id":3106,"building_id":3,"building_name":"성수 로프트","floor":1,"room_name":"106호","status":"OCCUPIED","application_id":690,"move_in_date":"2026-10-27","move_out_date":"2027-10-26","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"3201"}}
{"room_id":3201,"building_id":3,"building_name":"성수 로프트","floor":2,"room_name":"201호","status":"OCCUPIED","application_id":836,"move_in_date":"2026-10-01","move_out_date":"2027-09-30","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"3202"}}
{"room_id":3202,"building_id":3,"building_name":"성수 로프트","floor":2,"room_name":"202호","status":"OCCUPIED","application_id":837,"move_in_date":"2026-02-03","move_out_date":"2027-02-02","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"3203"}}
{"room_id":3203,"building_id":3,"building_name":"성수 로프트","floor":2,"room_name":"203호","status":"APPLYING","application_id":733,"move_in_date":"2026-11-20","move_out_date":"2027-05-19","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"3204"}}
{"room_id":3204,"building_id":3,"building_name":"성수 로프트","floor":2,"room_name":"204호","status":"OCCUPIED","application_id":838,"move_in_date":"2026-03-10","move_out_date":"2027-03-09","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"3205"}}
{"room_id":3205,"building_id":3,"building_name":"성수 로프트","floor":2,"room_name":"205호","status":"OCCUPIED","application_id":839,"move_in_date":"2026-04-01","move_out_date":"2027-03-31","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"3206"}}
{"room_id":3206,"building_id":3,"building_name":"성수 로프트","floor":2,"room_name":"206호","status":"OCCUPIED","application_id":840,"move_in_date":"2026-05-15","move_out_date":"2027-05-14","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"3301"}}
{"room_id":3301,"building_id":3,"building_name":"성수 로프트","floor":3,"room_name":"301호","status":"OCCUPIED","application_id":841,"move_in_date":"2026-06-01","move_out_date":"2027-05-31","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"3302"}}
{"room_id":3302,"building_id":3,"building_name":"성수 로프트","floor":3,"room_name":"302호","status":"OCCUPIED","application_id":842,"move_in_date":"2026-07-01","move_out_date":"2027-06-30","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"3303"}}
{"room_id":3303,"building_id":3,"building_name":"성수 로프트","floor":3,"room_name":"303호","status":"OCCUPIED","application_id":843,"move_in_date":"2026-08-10","move_out_date":"2027-02-09","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"3304"}}
{"room_id":3304,"building_id":3,"building_name":"성수 로프트","floor":3,"room_name":"304호","status":"MOVING_OUT","application_id":844,"move_in_date":"2026-09-01","move_out_date":"2026-12-10","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"3305"}}
{"room_id":3305,"building_id":3,"building_name":"성수 로프트","floor":3,"room_name":"305호","status":"OCCUPIED","application_id":845,"move_in_date":"2026-10-01","move_out_date":"2027-09-30","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"3306"}}
{"room_id":3306,"building_id":3,"building_name":"성수 로프트","floor":3,"room_name":"306호","status":"MOVE_IN_SCHEDULED","application_id":846,"move_in_date":"2026-11-25","move_out_date":"2027-05-24","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"3401"}}
{"room_id":3401,"building_id":3,"building_name":"성수 로프트","floor":4,"room_name":"401호","status":"OCCUPIED","application_id":847,"move_in_date":"2026-02-03","move_out_date":"2027-02-02","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"3402"}}
{"room_id":3402,"building_id":3,"building_name":"성수 로프트","floor":4,"room_name":"402호","status":"APPLYING","application_id":731,"move_in_date":"2026-12-01","move_out_date":"2027-05-31","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"3403"}}
{"room_id":3403,"building_id":3,"building_name":"성수 로프트","floor":4,"room_name":"403호","status":"OCCUPIED","application_id":848,"move_in_date":"2026-03-10","move_out_date":"2027-03-09","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"3404"}}
{"room_id":3404,"building_id":3,"building_name":"성수 로프트","floor":4,"room_name":"404호","status":"OCCUPIED","application_id":849,"move_in_date":"2026-04-01","move_out_date":"2027-03-31","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"3405"}}
{"room_id":3405,"building_id":3,"building_name":"성수 로프트","floor":4,"room_name":"405호","status":"OCCUPIED","application_id":850,"move_in_date":"2026-05-15","move_out_date":"2027-05-14","updated_at":"2026-10-20T00:00:00.000Z"}
{"index":{"_index":"room-status","_id":"3406"}}
{"room_id":3406,"building_id":3,"building_name":"성수 로프트","floor":4,"room_name":"406호","status":"OCCUPIED","application_id":851,"move_in_date":"2026-06-01","move_out_date":"2027-05-31","updated_at":"2026-10-20T00:00:00.000Z"}
```

### 3-3. 활동 로그 (14건)

```
POST _bulk
{"index":{"_index":"activity-log","_id":"PAYMENT-310"}}
{"event_id":"seed-PAYMENT-310","building_id":1,"building_name":"망원 스테이","activity_type":"PAYMENT","content":"망원 스테이 302호 결제 완료 (김지우, 2,780,000원)","status":"COMPLETED","source_service":"contract-service","source_ref_id":"310","occurred_at":"2026-10-28T01:12:00.000Z","updated_at":"2026-10-28T01:12:00.000Z"}
{"index":{"_index":"activity-log","_id":"RESERVATION-182"}}
{"event_id":"seed-RESERVATION-182","building_id":1,"building_name":"망원 스테이","activity_type":"RESERVATION","content":"공용 주방 예약 신청 (회원 5012)","status":"PENDING","source_service":"amenity-service","source_ref_id":"182","occurred_at":"2026-10-28T00:30:00.000Z","updated_at":"2026-10-28T00:30:00.000Z"}
{"index":{"_index":"activity-log","_id":"MOVE_IN-655"}}
{"event_id":"seed-MOVE_IN-655","building_id":1,"building_name":"망원 스테이","activity_type":"MOVE_IN","content":"망원 스테이 204호 입주 완료","status":"COMPLETED","source_service":"contract-service","source_ref_id":"655","occurred_at":"2026-10-27T05:50:00.000Z","updated_at":"2026-10-27T06:05:00.000Z"}
{"index":{"_index":"activity-log","_id":"CONTRACT-662"}}
{"event_id":"seed-CONTRACT-662","building_id":1,"building_name":"망원 스테이","activity_type":"CONTRACT","content":"망원 스테이 402호 퇴실 요청 (박도현)","status":"PENDING","source_service":"contract-service","source_ref_id":"662","occurred_at":"2026-10-27T03:20:00.000Z","updated_at":"2026-10-27T03:20:00.000Z"}
{"index":{"_index":"activity-log","_id":"RESERVATION-181"}}
{"event_id":"seed-RESERVATION-181","building_id":1,"building_name":"망원 스테이","activity_type":"RESERVATION","content":"공용 세탁실 예약 승인 (회원 5003)","status":"COMPLETED","source_service":"amenity-service","source_ref_id":"181","occurred_at":"2026-10-26T08:10:00.000Z","updated_at":"2026-10-26T09:45:00.000Z"}
{"index":{"_index":"activity-log","_id":"CONTRACT-720"}}
{"event_id":"seed-CONTRACT-720","building_id":2,"building_name":"연남 하우스","activity_type":"CONTRACT","content":"연남 하우스 203호 계약 신청 (최예린)","status":"PENDING","source_service":"contract-service","source_ref_id":"720","occurred_at":"2026-10-28T02:05:00.000Z","updated_at":"2026-10-28T02:05:00.000Z"}
{"index":{"_index":"activity-log","_id":"RESERVATION-190"}}
{"event_id":"seed-RESERVATION-190","building_id":2,"building_name":"연남 하우스","activity_type":"RESERVATION","content":"공용 라운지 예약 승인 (회원 5104)","status":"COMPLETED","source_service":"amenity-service","source_ref_id":"190","occurred_at":"2026-10-28T00:50:00.000Z","updated_at":"2026-10-28T01:30:00.000Z"}
{"index":{"_index":"activity-log","_id":"CONTRACT-705"}}
{"event_id":"seed-CONTRACT-705","building_id":2,"building_name":"연남 하우스","activity_type":"CONTRACT","content":"연남 하우스 105호 퇴실 요청 (정민호)","status":"PENDING","source_service":"contract-service","source_ref_id":"705","occurred_at":"2026-10-27T07:40:00.000Z","updated_at":"2026-10-27T07:40:00.000Z"}
{"index":{"_index":"activity-log","_id":"PAYMENT-295"}}
{"event_id":"seed-PAYMENT-295","building_id":2,"building_name":"연남 하우스","activity_type":"PAYMENT","content":"연남 하우스 301호 결제 완료 (오세진, 2,190,000원)","status":"COMPLETED","source_service":"contract-service","source_ref_id":"295","occurred_at":"2026-10-26T04:15:00.000Z","updated_at":"2026-10-26T04:15:00.000Z"}
{"index":{"_index":"activity-log","_id":"CONTRACT-733"}}
{"event_id":"seed-CONTRACT-733","building_id":3,"building_name":"성수 로프트","activity_type":"CONTRACT","content":"성수 로프트 203호 계약 신청 (한소희)","status":"PENDING","source_service":"contract-service","source_ref_id":"733","occurred_at":"2026-10-28T02:20:00.000Z","updated_at":"2026-10-28T02:20:00.000Z"}
{"index":{"_index":"activity-log","_id":"RESERVATION-205"}}
{"event_id":"seed-RESERVATION-205","building_id":3,"building_name":"성수 로프트","activity_type":"RESERVATION","content":"스터디룸 예약 승인 (회원 5231)","status":"COMPLETED","source_service":"amenity-service","source_ref_id":"205","occurred_at":"2026-10-28T00:10:00.000Z","updated_at":"2026-10-28T00:40:00.000Z"}
{"index":{"_index":"activity-log","_id":"CONTRACT-731"}}
{"event_id":"seed-CONTRACT-731","building_id":3,"building_name":"성수 로프트","activity_type":"CONTRACT","content":"성수 로프트 402호 계약 신청 (이도윤)","status":"PENDING","source_service":"contract-service","source_ref_id":"731","occurred_at":"2026-10-27T08:30:00.000Z","updated_at":"2026-10-27T08:30:00.000Z"}
{"index":{"_index":"activity-log","_id":"MOVE_IN-690"}}
{"event_id":"seed-MOVE_IN-690","building_id":3,"building_name":"성수 로프트","activity_type":"MOVE_IN","content":"성수 로프트 106호 입주 완료","status":"COMPLETED","source_service":"contract-service","source_ref_id":"690","occurred_at":"2026-10-27T01:30:00.000Z","updated_at":"2026-10-27T01:30:00.000Z"}
{"index":{"_index":"activity-log","_id":"PAYMENT-318"}}
{"event_id":"seed-PAYMENT-318","building_id":3,"building_name":"성수 로프트","activity_type":"PAYMENT","content":"성수 로프트 106호 결제 완료 (장현우, 3,920,000원)","status":"COMPLETED","source_service":"contract-service","source_ref_id":"318","occurred_at":"2026-10-26T06:00:00.000Z","updated_at":"2026-10-26T06:00:00.000Z"}
```

### 3-4. 확인

```
GET dashboard-snapshot/_count
GET room-status/_count
GET activity-log/_count
```

각각 **21, 59, 14**가 나오면 정상입니다.

### 3-5. 더미 데이터 지우기

인덱스는 그대로 두고 안의 문서만 지웁니다.

```
POST dashboard-snapshot/_delete_by_query
{ "query": { "match_all": {} } }

POST room-status/_delete_by_query
{ "query": { "match_all": {} } }

POST activity-log/_delete_by_query
{ "query": { "match_all": {} } }
```

## 4. 실행

| 서비스 | 방법 | 주소 |
|---|---|---|
| dashboard-service | IntelliJ에서 `DashboardServiceApplication` 실행 (Kafka, ES가 떠 있어야 함) | `localhost:8086` |
| api-gateway | admin-web이 호출하는 진입점 | `localhost:8081` |
| admin-web | `../../../frontends/admin-web`에서 `npm run dev` | `localhost:3000` |

admin-web은 대시보드 API를 게이트웨이로 직접 호출합니다. 주소는 `../../../frontends/admin-web/.env`의 `VITE_BACKEND_API_BASE_URL`(`http://localhost:8081`)이고, 경로는 `/dashboard-service/admin/**`입니다.