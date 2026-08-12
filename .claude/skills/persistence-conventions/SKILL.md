---
name: persistence-conventions
description: smash(ProvinceHow)의 DDD 헥사고날 구조에서 영속성을 다루는 규칙 — 도메인 모델과 JPA 엔티티 분리, Aggregate 간 참조를 ID 컬럼으로 두는 매핑, Repository 포트와 RepositoryAdapter/JpaMapper 패턴, JPQL 프로젝션, 값 객체 매핑, 이중 DataSource 환경의 @Transactional 경계, 배치 Upsert 규칙과 스키마 변경 주의점을 정의한다. 엔티티나 저장소를 만들거나 수정하거나, 쿼리를 추가하거나, 트랜잭션을 붙이거나, 배치 Writer를 작성할 때 사용한다. 계층 배치는 architecture-conventions, 명명·예외는 global-conventions, 유스케이스·테스트는 backend-conventions, 캐시는 redis-conventions를 따른다.
---

# persistence-conventions (DDD)

MySQL(**Docker 컨테이너**, 스키마 2개) · Spring Data JPA(Hibernate) · Spring Batch.
스키마는 **`hibernate.hbm2ddl.auto=update`** 로 JPA 엔티티에서 파생된다 → **`infrastructure/persistence`의 JpaEntity가 스키마의 정본**이다.

패키지는 `SDD.smash.domain.<context>.infrastructure.persistence` 다 → global-conventions §1

---

## 0. DB 인프라 (Docker 컨테이너)

DB는 **MySQL 컨테이너 1개에 스키마 2개**다. RDS가 아니다.

| DataSource | 프로퍼티 | 스키마 | 용도 | 트랜잭션 매니저 |
|---|---|---|---|---|
| `dataDBSource` (`@Primary` DataSource) | `spring.datasource-data.*` | `smash_data` | 업무 데이터 — 모든 JPA 엔티티 | `dataTransactionManager` (`JpaTransactionManager`) |
| `batchDataSource` (`@BatchDataSource`) | `spring.datasource-meta.*` | `smash_meta` | Spring Batch 메타 테이블 | `batchTransactionManager` (**`@Primary`**) |

- 드라이버는 **`com.mysql.cj.jdbc.Driver`** (AWS JDBC Wrapper 아님). JDBC URL 호스트는 compose 서비스명 **`mysql`**.
- 업무 테이블은 `hbm2ddl.auto=update`가 자동 생성한다.
- `smash_meta`는 `docker/mysql/init/01-init-meta-db.sh`가 최초 1회 만들고,
  배치 메타 테이블은 **`BATCH_SCHEMA_INIT=always`** 일 때 애플리케이션이 생성한다.
- 개발 중 DB를 완전히 초기화하려면 `docker compose down -v`. 운영 절차는 **seed-data** 스킬 참조.
- 수동 DDL은 `docker/mysql/ddl/`에 **날짜-목적** 이름으로 남긴다(예: `2026-08-11-drop-legacy-fk.sql`).

---

## 1. 원칙: 도메인 모델과 JPA 엔티티는 다른 클래스다

```
domain/model/DwellingMarket.java                          ← 비즈니스 규칙. JPA를 모른다
infrastructure/persistence/DwellingJpaEntity.java         ← 테이블 매핑. 규칙을 모른다
infrastructure/persistence/DwellingJpaMapper.java         ← 둘 사이 변환
infrastructure/persistence/DwellingJpaRepository.java     ← Spring Data 인터페이스
infrastructure/persistence/DwellingRepositoryAdapter.java ← domain/port 구현
```

이 분리가 헥사고날의 대가이자 이득이다.
- **대가**: 클래스와 매핑 코드가 늘어난다
- **이득**: 도메인이 JPA 제약(기본 생성자, 프록시, 지연로딩, `@Id` 요구)에서 자유로워지고, 테이블 구조를 바꿔도 도메인이 안 흔들린다

**예외 없이 분리한다.** 도메인 모델에 `@Entity`를 붙여 한 클래스로 합치지 않는다.

---

## 2. JPA 엔티티 (infrastructure/persistence)

### 2.1 표준 형태

```java
package SDD.smash.domain.dwelling.infrastructure.persistence;

@Entity
@Table(name = "dwelling",
       uniqueConstraints = @UniqueConstraint(name = "uk_dwelling_sigungu", columnNames = "sigungu_code"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)   // JPA 전용
@AllArgsConstructor
@Builder
public class DwellingJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 다른 Aggregate(Sigungu)를 FK 객체가 아니라 코드 값으로 참조한다 */
    @Column(name = "sigungu_code", length = 5, nullable = false)
    private String sigunguCode;

    @Column(name = "month_avg")  private Double  monthAvg;
    @Column(name = "month_mid")  private Integer monthMid;
    @Column(name = "jeonse_avg") private Double  jeonseAvg;
    @Column(name = "jeonse_mid") private Integer jeonseMid;
}
```

**규칙**
- 클래스명은 `<도메인개념>JpaEntity`. 테이블명은 `@Table(name = ...)`으로 **명시**한다(클래스명과 테이블명을 분리해 두기 위해 필수).
- `@NoArgsConstructor(access = PROTECTED)` — JPA는 기본 생성자를 요구하지만 외부에서 못 쓰게 막는다.
- `@Setter`를 붙이지 않는다. 변경이 필요하면 의미 있는 메서드를 만들거나 새 인스턴스를 만든다.
- **`@Data` / `@EqualsAndHashCode` / `@ToString` 금지.**
- 비즈니스 메서드를 넣지 않는다. **필드와 매핑만.**

### 2.2 Aggregate 간 참조 — 객체가 아니라 값 컬럼

```java
// ❌ 다른 Aggregate를 객체로 물지 않는다
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "sigungu_code", nullable = false)
private Sigungu sigungu;

// ✅ 코드 값으로만 참조한다
@Column(name = "sigungu_code", length = 5, nullable = false)
private String sigunguCode;
```

이 규칙의 효과:
- 지연 로딩·N+1·`LazyInitializationException`이 구조적으로 사라진다
- 다른 컨텍스트의 엔티티를 import할 이유가 없어져 **컨텍스트 경계가 컴파일 단위로 강제**된다
- 조인이 필요한 조회는 프로젝션 쿼리로 명시적으로 작성하게 된다(§4.3)

**Aggregate 내부**의 구성요소는 객체 참조를 유지해도 된다. 단 이 프로젝트에는 현재 그런 케이스가 없다.

> ⚠️ **`@MapsId`로 다른 Aggregate와 PK를 공유하는 테이블을 만들지 않는다.**
> - 배치가 적재하는 **원천 데이터**는 독립 JpaEntity로 만든다(PK는 자연키 String 또는 대리키).
> - **계산으로 매번 얻을 수 있는 파생값**(점수 등)은 테이블로 만들지 않고 `...Policy` + 캐시로 처리한다 → redis-conventions §1

### 2.3 식별자

| 유형 | 언제 | 매핑 |
|---|---|---|
| 자연키 String | 외부 부여 코드가 PK인 마스터 | `@Id @Column(name="sigungu_code", length=5) private String sigunguCode;` |
| 대리키 IDENTITY | 다대일 사실(fact) 테이블 | `@Id @GeneratedValue(strategy = IDENTITY) private Long id;` |

- 대리키는 **`Long`(래퍼)** 를 쓴다. primitive `long`을 쓰지 않는다.
- MySQL이므로 `GenerationType.IDENTITY` 고정. `AUTO`/`SEQUENCE` 금지.
- **도메인 모델의 식별자는 값 객체(`SigunguCode`)이고, JPA 엔티티의 식별자는 원시 타입(`String`)** 이다. 변환은 Mapper가 한다.

### 2.4 컬럼

```java
@Column(name = "ratio", precision = 18, scale = 2, nullable = false)
private BigDecimal ratio;                    // 금액·비율

@Column(name = "`count`", nullable = false)  // MySQL 예약어는 백틱
private Integer count;

@Enumerated(EnumType.STRING)                 // ORDINAL 금지
private Major major;
```

- 타입 규칙: 금액/비율 `BigDecimal`(precision/scale 명시), 개수 `Integer`, 평균 `Double`, 합계 `Long`.
- **`@Enumerated(EnumType.STRING)` 필수.**
- **Bean Validation(`@NotNull`, `@Min`, `@Max`)을 JPA 엔티티에 붙이지 않는다.** 검증은 도메인 값 객체(`Score`, `Money`)의 책임이다. DB 제약은 `nullable = false`로만 표현한다.

### 2.5 인덱스와 유니크 제약

```java
@Table(name = "infra",
       uniqueConstraints = @UniqueConstraint(name = "uk_infra_sigungu_industry",
                                             columnNames = {"sigungu_code", "industry_code"}),
       indexes = {@Index(name = "idx_infra_sigungu",  columnList = "sigungu_code"),
                  @Index(name = "idx_infra_industry", columnList = "industry_code")})
```

- 이름 규칙: `uk_<테이블>_<컬럼들>`, `idx_<테이블>_<컬럼>`
- **FK 객체 참조가 없으므로 Hibernate가 인덱스를 자동 생성하지 않는다.** 조인·필터에 쓰는 코드 컬럼에는 `@Index`를 **명시적으로 추가**해야 한다. 놓치면 조회 성능이 떨어진다.
- Upsert 대상 테이블에는 유니크 제약이 **반드시** 있어야 한다(§7).

> ⚠️ **`@Index`를 추가하기 전에 이미 커버되는지 먼저 확인한다.** 중복 인덱스는 쓰기 비용만 늘린다.
> - `@Column(unique = true)` → 그 컬럼에 유니크 인덱스가 이미 생긴다 (`population`/`dwelling`의 `sigungu_code`)
> - 복합 유니크의 **선두 컬럼**은 leftmost prefix로 커버된다 (`JobCount`의 `sigungu_code`).
>   **두 번째 이후 컬럼은 커버되지 않으므로** 단독 조회가 있으면 `@Index`가 필요하다
>   (`JobCount.job_code_middle_code` — `WHERE job_code_middle_code = ?` 단독 필터가 있다)
>
> ⚠️ **기존 DB에 `@Index`를 새로 선언할 때의 함정.** 옛 FK가 남긴 인덱스는 Hibernate 해시 이름
> (`FK6q4k2r...`)이라 이름이 달라, `hbm2ddl.auto=update`가 **같은 컬럼에 인덱스를 하나 더 만든다.**
> 해결은 옛 인덱스를 선언한 이름으로 **`RENAME INDEX`** 하는 것이다
> (`docker/mysql/ddl/2026-08-11-rename-fk-index.sql` 참고).
> `DROP INDEX`는 FK가 살아있는 동안 **errno 1553으로 거부**되지만 `RENAME INDEX`는 FK가 있어도
> 성공하고 FK가 개명된 인덱스를 계속 쓴다(MySQL 8.0 실측). 그래서 rename은 순서 의존성도 없다.

---

## 3. Mapper — 도메인 ↔ JPA

```java
package SDD.smash.domain.dwelling.infrastructure.persistence;

@Component
public class DwellingJpaMapper {

    public DwellingMarket toDomain(DwellingJpaEntity e) {
        return DwellingMarket.reconstitute(
                SigunguCode.of(e.getSigunguCode()),
                RentStat.of(e.getMonthAvg(), e.getMonthMid()),
                RentStat.of(e.getJeonseAvg(), e.getJeonseMid()));
    }

    public DwellingJpaEntity toJpaEntity(DwellingMarket m) {
        return DwellingJpaEntity.builder()
                .sigunguCode(m.sigunguCode().value())
                .monthAvg(m.monthly().average()).monthMid(m.monthly().median())
                .build();
    }
}
```

- **매핑 라이브러리(MapStruct 등)를 도입하지 않는다.** 손으로 쓴다 — 변환 규칙이 명시적으로 보이는 편이 낫다.
- `toDomain`은 **`reconstitute` 정적 팩토리**를 쓴다(저장소에서 복원하는 경로임을 드러낸다).
- 매핑은 순수 함수다. **왕복 변환 테스트**(`toDomain(toJpaEntity(m)).equals(m)`)를 쓴다 → backend-conventions §7.5
- `@Component`로 등록하거나 정적 메서드로 만든다. 둘 다 허용하되 한 컨텍스트 안에서는 통일한다.

---

## 4. Repository 포트와 어댑터

### 4.1 3층 구조

```java
// 1) domain/port — 도메인 언어의 인터페이스
public interface DwellingMarketRepository {
    Optional<DwellingMarket> findBy(SigunguCode code);
    List<DwellingMarket> findAll();
}

// 2) infrastructure/persistence — Spring Data 인터페이스 (기술)
public interface DwellingJpaRepository extends JpaRepository<DwellingJpaEntity, Long> {
    Optional<DwellingJpaEntity> findBySigunguCode(String sigunguCode);
}

// 3) infrastructure/persistence — 포트 구현
@Repository
@RequiredArgsConstructor
public class DwellingRepositoryAdapter implements DwellingMarketRepository {

    private final DwellingJpaRepository jpaRepository;
    private final DwellingJpaMapper mapper;

    @Override
    public Optional<DwellingMarket> findBy(SigunguCode code) {
        return jpaRepository.findBySigunguCode(code.value()).map(mapper::toDomain);
    }

    @Override
    public List<DwellingMarket> findAll() {
        return jpaRepository.findAll().stream().map(mapper::toDomain).toList();
    }
}
```

**규칙**
- **포트는 도메인이 필요로 하는 메서드만** 갖는다. `JpaRepository`의 전체 API를 노출하지 않는다.
- 포트 시그니처에 기술 타입이 없어야 한다: `String code`(❌) → `SigunguCode code`(✅)
- `@Repository`는 **어댑터에** 붙인다(예외 변환 목적). Spring Data 인터페이스에는 불필요하다.
- `DataDBConfig`의 `@EnableJpaRepositories(basePackages = ...)`는 **`SDD.smash.domain.<context>.infrastructure.persistence`를 하나씩 열거**한다. 도메인 패키지에 Spring Data 인터페이스가 생기는 실수를 부팅 시점에 막기 위한 것이다.
  > ⚠️ 이 목록은 **문자열**이라 컴파일러가 검증하지 않는다. 컨텍스트를 추가하거나 패키지를 옮기면 **여기도 같이 고친다.** 빠뜨리면 리포지토리 빈이 없어 부팅이 실패한다.

### 4.2 파생 쿼리

```java
Optional<DwellingJpaEntity> findBySigunguCode(String sigunguCode);
boolean existsBySigunguCode(String sigunguCode);
List<InfraJpaEntity> findAllBySigunguCode(String sigunguCode);
```

- Aggregate 간 객체 참조가 없으므로 **`findBySigungu_SigunguCode` 같은 언더스코어 경로 표현을 쓰지 않는다.** 그냥 `findBySigunguCode`다.

### 4.3 조회 전용 프로젝션 (CQRS-lite)

여러 테이블을 합치는 화면용 조회는 Aggregate를 거치지 않고 **조회 모델**을 직접 채운다. 이때도 포트를 통한다.

```java
// domain/port — 조회 전용 포트
public interface RegionCodeQuery {
    List<RegionCodeView> findAllRegionCodes();
    Optional<RegionCodeView> findBy(SigunguCode code);
}

// infrastructure/persistence — JPQL 생성자 프로젝션
public interface SigunguJpaRepository extends JpaRepository<SigunguJpaEntity, String> {

    @Query("""
        SELECT new SDD.smash.domain.address.infrastructure.persistence.projection.RegionCodeRow(
            sd.sidoCode, sd.name, sg.sigunguCode, sg.name)
        FROM SigunguJpaEntity sg
        JOIN SidoJpaEntity sd ON sd.sidoCode = sg.sidoCode
        WHERE sg.sigunguCode = :sigunguCode
    """)
    Optional<RegionCodeRow> findRegionCode(@Param("sigunguCode") String sigunguCode);
}
```

**규칙**
- **Aggregate 간 객체 참조가 없으므로 암시적 조인이 불가능하다.** 조인은 `JOIN ... ON`으로 **명시**한다. (`sgg.sido.name` 같은 경로 표현은 쓸 수 없다.)
- 프로젝션 대상은 `infrastructure/persistence/projection`의 **기술 DTO(`...Row`)** 다. 어댑터가 이를 `application/dto`의 `...View`로 변환한다. 도메인 타입을 JPQL `new`로 직접 만들지 않는다(값 객체 생성자 검증이 쿼리 실행 중에 터질 수 있다).
- `new` 뒤에는 **FQCN**, 텍스트 블록(`"""`), `@Param` **명시**.
  > ⚠️ 이 FQCN은 **문자열**이다. 패키지를 옮기면 컴파일은 통과하고 **쿼리 시점/부팅 시점에 터진다.** 이동 후 반드시 전수 확인한다 → architecture-conventions §9.3
- 집계 타입 주의: `SUM(int)` → **`Long`**.
- **N번 조회를 1번 조인으로.** 같은 테이블을 조건만 바꿔 여러 번 조회하고 있으면 `GROUP BY` 단일 쿼리로 합칠 수 있는지 본다.

### 4.4 반환 타입

- 단건 `Optional<T>`, 다건 `List<T>`(빈 리스트), 존재확인 `boolean`.
- **프로젝션이 `null`을 반환하게 두지 않는다.** 단건은 전부 `Optional`이다.

---

## 5. 값 객체 매핑

값 객체는 **JPA 엔티티에 원시 타입으로 풀어서** 저장하는 것을 기본으로 한다(§2.1). 매핑은 Mapper가 한다.

`@Embeddable`/`AttributeConverter`는 아래 조건에서만 쓴다.

```java
// 단일 값 객체를 컬럼 하나로 — AttributeConverter
@Converter(autoApply = false)
public class SigunguCodeConverter implements AttributeConverter<SigunguCode, String> {
    public String convertToDatabaseColumn(SigunguCode c) { return c == null ? null : c.value(); }
    public SigunguCode convertToEntityAttribute(String s) { return s == null ? null : SigunguCode.of(s); }
}
```

- **주의**: `AttributeConverter`를 쓰면 **JPA 엔티티가 도메인 타입을 import**하게 되어 분리가 흐려진다. 그리고 DB에 이미 들어있는 잘못된 값이 조회 시점에 `DomainException`을 던진다.
- 따라서 **기본은 Mapper 방식**이고, 컬럼이 많아 매핑이 번거로운 경우에만 `@Embeddable`을 고려한다.
- `@Embeddable`을 쓸 때는 `infrastructure/persistence`에 **JPA 전용 임베더블**을 따로 만든다. 도메인 `record`에 `@Embeddable`을 붙이지 않는다.

---

## 6. 트랜잭션 경계

### 6.1 핵심 함정

`@Primary` `PlatformTransactionManager`는 **`batchTransactionManager`(meta DB용 `DataSourceTransactionManager`)** 다.

```java
@Transactional(readOnly = true)                                                 // ❌ meta DB 트랜잭션
@Transactional(transactionManager = "dataTransactionManager", readOnly = true)  // ✅ JPA 트랜잭션
```

무수식 `@Transactional`은 JPA 영속성 컨텍스트가 참여하지 않아 원자성도, `readOnly` 최적화도 얻지 못한다.

### 6.2 어디에 붙이는가

| 계층 | 규칙 |
|---|---|
| `presentation` | 붙이지 않는다 |
| **`application` public 메서드** | **트랜잭션 경계.** `@Transactional(transactionManager = "dataTransactionManager", readOnly = true)` |
| `domain` | 붙이지 않는다 (Spring을 모른다) |
| `infrastructure/persistence` 어댑터 | 붙이지 않는다. 상위 트랜잭션에 참여 |
| `infrastructure/batch` | 붙이지 않는다. `StepBuilder.chunk(size, txManager)`가 경계 |
| `infrastructure/scheduler` | 붙이지 않는다. 유스케이스에 위임 |

### 6.3 규칙

1. **조회는 전부 `readOnly = true`.** 이 시스템의 API 경로에는 쓰기가 없다.
2. `transactionManager = "dataTransactionManager"` **필수**.
3. **트랜잭션 안에서 캐시·외부 API를 호출하지 않는다.** 커넥션을 쥔 채 네트워크를 기다리게 된다.
   - 유스케이스가 캐시와 DB를 모두 쓰면, **DB 조회 구간만** 별도 메서드로 잘라 트랜잭션을 건다.
4. **한 트랜잭션에서 하나의 Aggregate만 변경한다.** 여러 Aggregate 갱신이 필요하면 유스케이스를 나누거나 최종 일관성을 받아들인다.
5. `private` 메서드/self-invocation에는 프록시가 걸리지 않는다.

---

## 7. 배치 영속성 (infrastructure/batch)

### 7.1 Writer 선택

| Writer | 언제 |
|---|---|
| `JdbcBatchItemWriter` + `ON DUPLICATE KEY UPDATE` | **Upsert(재실행 안전)**, 대량. 기본 선택 |
| `RepositoryItemWriter` | 단순 신규 저장, 소량 |

### 7.2 표준형

```java
@Bean
public JdbcBatchItemWriter<DwellingUpsertRow> dwellingWriter() {
    final String sql = """
        INSERT INTO dwelling (sigungu_code, month_avg, month_mid, jeonse_avg, jeonse_mid)
        VALUES (:sigunguCode, :monthAvg, :monthMid, :jeonseAvg, :jeonseMid)
        ON DUPLICATE KEY UPDATE
            month_avg = VALUES(month_avg), month_mid = VALUES(month_mid),
            jeonse_avg = VALUES(jeonse_avg), jeonse_mid = VALUES(jeonse_mid)
        """;
    return new JdbcBatchItemWriterBuilder<DwellingUpsertRow>()
            .dataSource(dataDataSource)                       // @Qualifier("dataDBSource") 필수
            .sql(sql)
            .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
            .assertUpdates(false)
            .build();
}
```

**규칙**
- **`@Qualifier("dataDBSource") DataSource`를 주입**받는다. 아니면 meta DB에 쓴다.
- 네임드 파라미터명 == Upsert Row의 필드명. 한쪽만 바꾸면 런타임에 깨진다.
- 대상 테이블에 **유니크 제약**이 있는지 먼저 확인한다.
- **대량 적재는 Aggregate를 거치지 않아도 된다.** 성능상 허용되는 예외다.
- 다만 **도메인 불변식은 Processor에서 값 객체 생성으로 검증**한다.
  ```java
  return item -> {
      try { SigunguCode.of(normalize(item.getSigunguCode())); }
      catch (DomainException e) { return null; }        // skip
      return toUpsertRow(item);
  };
  ```
- 배치 DTO는 전부 **기술 DTO**다(`infrastructure/batch/dto`). 도메인 모델을 배치 Reader/Writer 타입으로 쓰지 않는다.
- 외부 데이터 정제(`normalize`, `addLeadingZero`)는 배치 안에서 끝낸다. 도메인은 이미 정제된 값만 본다.

### 7.3 실행 순서

Runner의 `@Order`가 적재 선후를 통제한다(1 Sido → 9 Dwelling). 물리 FK가 아니라 **적재 순서와 Processor 검증**이 참조 무결성을 보장하므로 이 순서를 임의로 바꾸지 않는다. → architecture-conventions §6.2

---

## 8. 스키마 변경 주의

`hbm2ddl.auto=update`는 **추가만** 반영한다. 삭제·타입 축소·제약 제거는 반영하지 않는다.

| 하려는 것 | `update`가 해주는가 | 대응 |
|---|---|---|
| 컬럼 추가 | ✅ | 그대로 진행. `nullable` 기본값 주의 |
| 인덱스/유니크 추가 | ✅ (이름이 다르면 **중복 생성**) | §2.5의 `RENAME INDEX` 함정 확인 |
| 컬럼 삭제 / 타입 축소 | ❌ | `docker/mysql/ddl/`에 DDL 스크립트를 만들고 팀에 공유 |
| FK 제약 삭제 | ❌ | 같음 (`2026-08-11-drop-legacy-fk.sql` 참고) |
| 테이블명·컬럼명 변경 | ❌ (**새 테이블/컬럼을 만든다**) | 원칙적으로 하지 않는다. 꼭 필요하면 DDL로 rename |

- **테이블명·컬럼명을 바꾸지 않는 것이 제1원칙**이다. 이름을 바꾸면 `update` 전략이 새 테이블/컬럼을 만들고 데이터가 분리된다.
- 클래스명을 바꿔도 `@Table(name = ...)`이 명시돼 있으면 스키마 영향이 없다(§2.1).
- 파괴적 변경은 **별도 DDL 스크립트**로 하고, 적용 여부를 팀에 공유한다.

---

## 9. 체크리스트

**JPA 엔티티**
- [ ] `infrastructure/persistence`에 있고 이름이 `...JpaEntity`인가
- [ ] `@Table(name = ...)`으로 테이블명을 명시했는가
- [ ] 다른 Aggregate를 **객체가 아니라 코드 컬럼**으로 참조하는가
- [ ] `@NoArgsConstructor(PROTECTED)`이고 `@Setter`/`@Data`가 없는가
- [ ] Bean Validation 대신 값 객체에 검증을 두었는가
- [ ] enum에 `@Enumerated(EnumType.STRING)`이 있는가
- [ ] 조인·필터에 쓰는 코드 컬럼에 `@Index`를 명시했는가 (중복 인덱스는 아닌가)

**포트/어댑터**
- [ ] `domain/port` 인터페이스에 기술 타입(`String code`, JPA 타입)이 없는가
- [ ] 어댑터가 포트를 구현하고 Mapper로 변환하는가
- [ ] 포트가 필요한 메서드만 갖는가 (`JpaRepository` 전체 노출 없음)
- [ ] 단건 조회가 `Optional`인가
- [ ] JPQL 프로젝션에 FQCN·텍스트 블록·`@Param`·명시적 `JOIN`이 있는가
- [ ] 새 패키지를 `@EnableJpaRepositories`의 basePackages에 추가했는가

**트랜잭션**
- [ ] `application` 계층에만 `@Transactional`이 있는가
- [ ] `transactionManager = "dataTransactionManager"`가 지정됐는가
- [ ] 조회에 `readOnly = true`가 있는가
- [ ] 트랜잭션 안에서 캐시/외부 API를 호출하지 않는가

**배치**
- [ ] `JdbcBatchItemWriter`가 `@Qualifier("dataDBSource")`를 쓰는가
- [ ] SQL 네임드 파라미터와 Row 필드명이 일치하는가
- [ ] Processor가 값 객체 생성으로 불변식을 검증하고 실패 시 `null`(skip)을 반환하는가
- [ ] 테이블명·컬럼명을 바꾸지 않았는가
