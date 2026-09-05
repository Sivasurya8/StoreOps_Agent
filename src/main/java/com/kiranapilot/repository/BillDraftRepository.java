package com.kiranapilot.repository;

import com.kiranapilot.entity.BillDraft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BillDraftRepository extends JpaRepository<BillDraft, Long> {
}
