package com.aerovhyn.domain.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "blockchain")
public class BlockchainEntity {

    @Id
    private Long idx;

    @Column(nullable = false)
    private String timestamp;

    @Column(nullable = false, columnDefinition = "text")
    private String data;

    @Column(name = "prev_hash", nullable = false)
    private String prevHash;

    @Column(nullable = false)
    private String hash;

    @Column(columnDefinition = "integer default 0")
    private Integer nonce = 0;

    public BlockchainEntity() {}

    public BlockchainEntity(Long idx, String timestamp, String data, String prevHash, String hash, Integer nonce) {
        this.idx = idx;
        this.timestamp = timestamp;
        this.data = data;
        this.prevHash = prevHash;
        this.hash = hash;
        this.nonce = nonce;
    }

    public Long getIdx() { return idx; }
    public void setIdx(Long idx) { this.idx = idx; }
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    public String getData() { return data; }
    public void setData(String data) { this.data = data; }
    public String getPrevHash() { return prevHash; }
    public void setPrevHash(String prevHash) { this.prevHash = prevHash; }
    public String getHash() { return hash; }
    public void setHash(String hash) { this.hash = hash; }
    public Integer getNonce() { return nonce; }
    public void setNonce(Integer nonce) { this.nonce = nonce; }
}
