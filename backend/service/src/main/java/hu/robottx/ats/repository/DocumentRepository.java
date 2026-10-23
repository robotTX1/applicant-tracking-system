package hu.robottx.ats.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import hu.robottx.ats.entity.Document;

public interface DocumentRepository extends JpaRepository<Document, Long> {
}
