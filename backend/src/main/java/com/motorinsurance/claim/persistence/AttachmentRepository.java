package com.motorinsurance.claim.persistence;

import com.motorinsurance.claim.domain.Attachment;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {
}
