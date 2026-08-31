package com.motorinsurance.pricing.persistence;

import com.motorinsurance.pricing.domain.BonusMalusClass;
import org.springframework.data.jpa.repository.JpaRepository;

/** {@code findById(code)} is the whole lookup - a miss means "unknown class", not "unset" (mirrors {@code InstallmentPlanRepository}). */
public interface BonusMalusClassRepository extends JpaRepository<BonusMalusClass, String> {
}
