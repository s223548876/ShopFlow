package com.shopflow.catalog;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query(
            value = """
                    SELECT p FROM Product p
                    JOIN FETCH p.category c
                    WHERE (:active IS NULL OR p.active = :active)
                      AND (:categoryId IS NULL OR c.id = :categoryId)
                      AND (CAST(:q AS string) IS NULL
                           OR p.name ILIKE CONCAT('%', CAST(:q AS string), '%')
                           OR p.description ILIKE CONCAT('%', CAST(:q AS string), '%'))
                    """,
            countQuery = """
                    SELECT COUNT(p) FROM Product p
                    WHERE (:active IS NULL OR p.active = :active)
                      AND (:categoryId IS NULL OR p.category.id = :categoryId)
                      AND (CAST(:q AS string) IS NULL
                           OR p.name ILIKE CONCAT('%', CAST(:q AS string), '%')
                           OR p.description ILIKE CONCAT('%', CAST(:q AS string), '%'))
                    """
    )
    Page<Product> search(
            @Param("active") Boolean active,
            @Param("categoryId") Long categoryId,
            @Param("q") String q,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "category")
    Optional<Product> findByIdAndActiveTrue(Long id);

    @EntityGraph(attributePaths = "category")
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findWithCategoryById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id IN :ids ORDER BY p.id")
    List<Product> findAllByIdInOrderByIdForUpdate(@Param("ids") Collection<Long> ids);
}
