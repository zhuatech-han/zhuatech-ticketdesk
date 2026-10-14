-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
-- 座位、订单、票与资金核销记录；不包含虚构客户资料。
CREATE TABLE ticket_seat (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 event_id VARCHAR(36) NOT NULL, label VARCHAR(10) NOT NULL,
 price BIGINT NOT NULL, currency VARCHAR(3) NOT NULL,
 current_booking_id VARCHAR(36),
 CONSTRAINT uq_ticket_seat UNIQUE(event_id,label), CONSTRAINT ck_seat_price CHECK(price>=0)
);
CREATE TABLE ticket_booking (
 id VARCHAR(36) PRIMARY KEY, seat_id BIGINT NOT NULL,
 owner_id VARCHAR(64) NOT NULL, price BIGINT NOT NULL,
 currency VARCHAR(3) NOT NULL, expires_at BIGINT NOT NULL, state VARCHAR(20) NOT NULL,
 CONSTRAINT fk_booking_seat FOREIGN KEY(seat_id) REFERENCES ticket_seat(id),
 CONSTRAINT ck_booking_price CHECK(price>=0),
 CONSTRAINT ck_booking_state CHECK(state IN ('HELD','PAID','EXPIRED','REFUND_PENDING','REFUNDED'))
);
ALTER TABLE ticket_seat ADD CONSTRAINT fk_seat_booking FOREIGN KEY(current_booking_id) REFERENCES ticket_booking(id);
CREATE TABLE ticket_ticket (
 id VARCHAR(36) PRIMARY KEY, booking_id VARCHAR(36) NOT NULL UNIQUE,
 state VARCHAR(20) NOT NULL,
 CONSTRAINT fk_ticket_booking FOREIGN KEY(booking_id) REFERENCES ticket_booking(id),
 CONSTRAINT ck_ticket_state CHECK(state IN ('ISSUED','CHECKED_IN','VOID'))
);
CREATE TABLE ticket_receipt (
 created_at BIGINT NOT NULL,
 id VARCHAR(36) PRIMARY KEY, booking_id VARCHAR(36) NOT NULL,
 kind VARCHAR(20) NOT NULL, amount BIGINT NOT NULL, currency VARCHAR(3) NOT NULL,
 result VARCHAR(20) NOT NULL, ticket_id VARCHAR(36),
 CONSTRAINT fk_receipt_booking FOREIGN KEY(booking_id) REFERENCES ticket_booking(id),
 CONSTRAINT fk_receipt_ticket FOREIGN KEY(ticket_id) REFERENCES ticket_ticket(id),
 CONSTRAINT ck_receipt_amount CHECK(amount>=0), CONSTRAINT ck_receipt_kind CHECK(kind IN ('PAYMENT','REFUND'))
);
CREATE TABLE ticket_scan (
 created_at BIGINT NOT NULL,
 id BIGINT AUTO_INCREMENT PRIMARY KEY, event_id VARCHAR(36) NOT NULL,
 operator_id VARCHAR(64) NOT NULL, ticket_id VARCHAR(36), decision VARCHAR(20) NOT NULL,
 CONSTRAINT fk_scan_ticket FOREIGN KEY(ticket_id) REFERENCES ticket_ticket(id)
);
CREATE INDEX ix_booking_seat ON ticket_booking(seat_id);
CREATE INDEX ix_scan_event ON ticket_scan(event_id,decision);
