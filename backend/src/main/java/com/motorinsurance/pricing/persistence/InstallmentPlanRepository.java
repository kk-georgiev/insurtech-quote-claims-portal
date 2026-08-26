package com.motorinsurance.pricing.persistence;

import com.motorinsurance.pricing.domain.InstallmentPlan;
import org.springframework.data.jpa.repository.JpaRepository;

/** {@code findById(installments)} is the whole lookup - a miss means "unsupported count", not "unset". */
public interface InstallmentPlanRepository extends JpaRepository<InstallmentPlan, Short> {
}
