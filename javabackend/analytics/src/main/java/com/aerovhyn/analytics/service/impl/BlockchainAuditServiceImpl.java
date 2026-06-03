package com.aerovhyn.analytics.service.impl;

import com.aerovhyn.analytics.service.BlockchainAuditService;
import com.aerovhyn.common.dto.BlockchainBlockDto;
import com.aerovhyn.common.dto.VerificationResultDto;
import com.aerovhyn.domain.entity.BlockchainEntity;
import com.aerovhyn.domain.repository.BlockchainRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

@Service
public class BlockchainAuditServiceImpl implements BlockchainAuditService {

    private static final Logger log = LoggerFactory.getLogger(BlockchainAuditServiceImpl.class);
    private final BlockchainRepository blockchainRepository;
    private final ObjectMapper objectMapper;

    public BlockchainAuditServiceImpl(BlockchainRepository blockchainRepository, ObjectMapper objectMapper) {
        this.blockchainRepository = blockchainRepository;
        this.objectMapper = objectMapper.copy();
        this.objectMapper.configure(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    @jakarta.annotation.PostConstruct
    public synchronized void init() {
        if (blockchainRepository.count() == 0) {
            createGenesisBlock();
        }
    }

    private void createGenesisBlock() {
        Map<String, Object> blockData = new java.util.LinkedHashMap<>();
        blockData.put("event", "SYSTEM_GENESIS");
        blockData.put("message", "AEROVHYN Tamper-Evident Audit Log initialized");
        blockData.put("system_version", "2.0.0");
        String timestamp = Instant.now().toString();
        addBlockInternal(0L, timestamp, blockData, "0".repeat(64));
        log.info("Tamper-evident ledger genesis block initialized.");
    }

    private BlockchainBlockDto addBlockInternal(long idx, String timestamp, Map<String, Object> data, String prevHash) {
        String dataStr;
        try {
            dataStr = objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            dataStr = data.toString();
        }

        String blockStr = idx + "|" + timestamp + "|" + dataStr + "|" + prevHash;
        String blockHash = sha256(blockStr);

        BlockchainEntity entity = new BlockchainEntity(idx, timestamp, dataStr, prevHash, blockHash, 0);
        blockchainRepository.save(entity);

        return new BlockchainBlockDto(idx, timestamp, data, prevHash, blockHash);
    }

    @Override
    public synchronized BlockchainBlockDto addBlock(Map<String, Object> data) {
        if (blockchainRepository.count() == 0) {
            createGenesisBlock();
        }

        Optional<BlockchainEntity> lastBlockOpt = blockchainRepository.findTopByOrderByIdxDesc();

        long newIdx = lastBlockOpt.map(b -> b.getIdx() + 1).orElse(0L);
        String timestamp = Instant.now().toString();
        String prevHash = lastBlockOpt.map(BlockchainEntity::getHash).orElse("0".repeat(64));

        return addBlockInternal(newIdx, timestamp, data, prevHash);
    }

    @Override
    public VerificationResultDto verifyChain() {
        List<BlockchainEntity> chain = blockchainRepository.findAllByOrderByIdxAsc();

        if (chain.isEmpty()) {
            return new VerificationResultDto(false, null, 0, 0, null, "Chain is empty");
        }

        int blocksChecked = 0;
        for (int i = 0; i < chain.size(); i++) {
            BlockchainEntity block = chain.get(i);
            String blockStr = block.getIdx() + "|" + block.getTimestamp() + "|" + block.getData() + "|" + block.getPrevHash();
            String computedHash = sha256(blockStr);

            if (!computedHash.equals(block.getHash())) {
                return new VerificationResultDto(false, block.getIdx(), blocksChecked,
                        chain.size(), null, "Hash mismatch at block " + block.getIdx());
            }

            if (i > 0) {
                BlockchainEntity prev = chain.get(i - 1);
                if (!block.getPrevHash().equals(prev.getHash())) {
                    return new VerificationResultDto(false, block.getIdx(), blocksChecked,
                            chain.size(), null, "Chain broken at block " + block.getIdx());
                }
            }
            blocksChecked++;
        }

        return new VerificationResultDto(true, null, blocksChecked,
                chain.size(), chain.get(chain.size() - 1).getHash(),
                "Audit Log integrity verified — all hashes valid");
    }

    @Override
    public List<BlockchainBlockDto> getChain(int limit) {
        List<BlockchainEntity> entities = blockchainRepository.findAllByOrderByIdxDesc(org.springframework.data.domain.PageRequest.of(0, limit));
        return entities.stream().map(e -> {
            try {
                Object data = objectMapper.readValue(e.getData(), Object.class);
                return new BlockchainBlockDto(e.getIdx(), e.getTimestamp(), data, e.getPrevHash(), e.getHash());
            } catch (JsonProcessingException ex) {
                return new BlockchainBlockDto(e.getIdx(), e.getTimestamp(), e.getData(), e.getPrevHash(), e.getHash());
            }
        }).toList();
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
