package com.coliving.platform.contract.repository;

import com.coliving.platform.contract.entity.PaymentReceipt;
import com.coliving.platform.contract.enums.ReceiptType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * @author 한성현
 */
public interface PaymentReceiptRepository extends JpaRepository<PaymentReceipt, Long> {

    // (payment_id, receipt_type) UNIQUE → 최대 1건
    Optional<PaymentReceipt> findByPaymentIdAndReceiptType(Long paymentId, ReceiptType receiptType);
}
