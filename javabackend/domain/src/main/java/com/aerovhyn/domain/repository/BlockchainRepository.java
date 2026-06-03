package com.aerovhyn.domain.repository;

import com.aerovhyn.domain.entity.BlockchainEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BlockchainRepository extends JpaRepository<BlockchainEntity, Long> {

    Optional<BlockchainEntity> findTopByOrderByIdxDesc();

    List<BlockchainEntity> findAllByOrderByIdxAsc();

    List<BlockchainEntity> findTop50ByOrderByIdxDesc();

    List<BlockchainEntity> findAllByOrderByIdxDesc(Pageable pageable);
}

