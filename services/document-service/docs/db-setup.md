# document-service DB 설정

계약서 양식(`document_forms`)은 PostgreSQL의 `document_service` DB에 저장합니다. 아래 순서로 처음 한 번만 만듭니다.

## 1. DBeaver 설정 (한 번만)

양식 본문의 `{{applicantName}}` 같은 빈칸 표기를 DBeaver가 매개변수로 착각해 값 입력 팝업을 띄웁니다. 팝업이 뜨면 **확인을 누르지 말고 취소**한 뒤 아래 설정을 끄세요.

`Window > Preferences > Editors > SQL Editor > SQL Processing`에서 두 항목을 해제합니다.

- `Enable SQL parameters`
- `Enable variables`

## 2. DB 만들기

기본 DB(`postgres`)에 접속해서 실행합니다. 이미 있으면 건너뜁니다.

```sql
CREATE DATABASE document_service ENCODING 'UTF8';
```

## 3. 테이블 만들기

`document_service` DB로 접속을 바꾼 뒤 `Alt+X`(스크립트 실행)로 실행합니다. 테이블이 없을 때만 만들고, 기존 데이터는 지우지 않습니다.

```sql
CREATE TABLE IF NOT EXISTS document_forms (
    id          BIGSERIAL    PRIMARY KEY,
    language    VARCHAR(10)  NOT NULL DEFAULT 'ko' CHECK (language IN ('ko', 'en')),
    title       VARCHAR(200) NOT NULL,
    content     TEXT         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE document_forms IS '계약서 양식. language 는 ko 또는 en';
COMMENT ON COLUMN document_forms.content IS '빈칸이 있는 본문(HTML)';
```

## 4. 기본 양식 넣기

같은 DB에서 `Alt+X`로 실행합니다. **한 번만 실행하세요. 다시 실행하면 같은 양식이 한 번 더 들어갑니다.**

```sql
INSERT INTO document_forms (language, title, content) VALUES
('ko', '기본 임대차 계약서', $$<h1>임대차 계약서</h1>
<p>임차인: {{applicantName}} ({{applicantEmail}}, {{applicantPhone}})</p>
<p>주소: {{applicantAddress}}</p>
<p>대상 호실: {{buildingName}} {{roomName}}</p>
<p>입주일: {{moveInDate}} / 계약 기간: {{contractMonths}}</p>
<p>월 이용료: {{monthlyFee}} / 보증금: {{deposit}}</p>
<p>첫 결제 금액: {{firstPayment}}</p>$$),
('en', 'Standard Lease Agreement', $$<h1>Lease Agreement</h1>
<p>Tenant: {{applicantName}} ({{applicantEmail}}, {{applicantPhone}})</p>
<p>Address: {{applicantAddress}}</p>
<p>Premises: {{buildingName}} {{roomName}}</p>
<p>Move-in: {{moveInDate}} / Term: {{contractMonths}}</p>
<p>Monthly fee: {{monthlyFee}} / Deposit: {{deposit}}</p>
<p>First payment: {{firstPayment}}</p>$$);
```

계약서에는 언어별로 가장 늦게 등록된(번호가 가장 큰) 양식이 쓰입니다. 한국어, 영어 양식이 각각 하나 이상 있어야 해당 언어로 계약서를 만들 수 있습니다.
