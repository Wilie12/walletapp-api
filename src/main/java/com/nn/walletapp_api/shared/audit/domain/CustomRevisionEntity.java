package com.nn.walletapp_api.shared.audit.domain;

import com.nn.walletapp_api.shared.audit.listener.SecurityContextRevisionListener;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;

import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "revinfo")
@RevisionEntity(SecurityContextRevisionListener.class)
@Getter
@Setter
@NoArgsConstructor
public class CustomRevisionEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "revinfo_seq_gen")
    @SequenceGenerator(name = "revinfo_seq_gen", sequenceName = "revinfo_seq", allocationSize = 50)
    @RevisionNumber
    @Column(name = "rev")
    private long id;

    @RevisionTimestamp
    @Column(name = "revtstmp", nullable = false)
    private long timestamp;

    @Column(name = "author_id")
    private UUID authorId;
}
