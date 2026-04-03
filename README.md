# TT_LuckyTower

마인크래프트 JE 1.20.1용 럭키타워 도박 미니게임 플러그인입니다.
플레이어가 레드스톤 램프로 구성된 타워를 자동으로 오르며 층마다 성공/실패/리셋 확률 판정을 받고, 누적 보상을 획득하는 도파민성 게임 기기입니다.

| 항목 | 내용 |
|------|------|
| 에디션 | Java Edition |
| 지원 버전 | 1.20.1 |
| 서버 소프트웨어 | Paper 1.20.1 이상 (Purpur 등 Paper 기반 포크 호환) |

> **다른 버전 사용 시**: 소스코드를 직접 수정하여 빌드해 사용하시기 바랍니다.
> 버전별 호환 이슈(사운드 enum, API 변경 등)는 직접 대응이 필요합니다.

---

## 목차

- [기능 개요](#기능-개요)
- [의존성](#의존성)
- [설치 방법](#설치-방법)
- [게임 구조](#게임-구조)
- [명령어](#명령어)
- [Config 설정](#config-설정)
- [타워별 YAML 구조](#타워별-yaml-구조)
- [잭팟 시스템](#잭팟-시스템)
- [보상 타입](#보상-타입)
- [부스트 아이템](#부스트-아이템)
- [빌드](#빌드)

---

## 기능 개요

| 기능 | 설명 |
|------|------|
| 층별 확률 판정 | 성공 / 실패 / 리셋 3가지 결과 |
| 누적 보상 시스템 | 올라갈수록 보상이 쌓임 |
| 리셋 층계 | 해당 층 도달 시 누적 보상 즉시 지급 (체크포인트) |
| 잭팟 풀 | 입장료 일부가 풀에 적립, 리셋 층계에서 확률 당첨 |
| 그룹 잭팟 공유 | 같은 그룹의 여러 타워가 잭팟 풀 공유 |
| 부스트 아이템 | 특정 아이템 소모 → 실패 확률에서 차감해 성공 확률 증가 |
| 홀로그램 | DecentHolograms로 실시간 잭팟 금액 표시 |
| 구역 버프 | 특정 층 클리어 시 WorldGuard 리전 내 전원 버프 |
| 자동 설치 | `/lt start` 명령어로 레드스톤 램프 + 버튼 자동 배치 |
| 확률표 | `/확률표` 명령어로 층별 확률 조회 |

---

## 의존성

### 필수
| 플러그인 | 버전 | 용도 |
|----------|------|------|
| [Paper](https://papermc.io/) | 1.20.1 | 서버 API |
| [WorldGuard](https://dev.bukkit.org/projects/worldguard) | 7.0.9+ | 리전 관리 |
| [Vault](https://www.spigotmc.org/resources/vault.34315/) | 1.7.1+ | 경제 시스템 |

### 선택 (없어도 작동)
| 플러그인 | 버전 | 용도 |
|----------|------|------|
| [DecentHolograms](https://www.spigotmc.org/resources/decentholograms.96927/) | 2.8.9+ | 잭팟 금액 홀로그램 |
| [MMOItems](https://www.spigotmc.org/resources/mmoitems-premium.39267/) | 6.9.4+ | MMO 아이템 보상 지급 |

---

## 설치 방법

1. `TT_LuckyTower.jar`를 서버 `plugins/` 폴더에 넣기
2. 서버 시작 → `plugins/TT_LuckyTower/` 자동 생성
3. `config.yml`에서 전역 설정 수정
4. `plugins/TT_LuckyTower/towers/` 폴더에 타워별 YAML 파일 작성
5. `/lt reload` 로 설정 반영

---

## 게임 구조

```
[플레이어] 상호작용 블록 우클릭
    → GUI 오픈 (부스트 여부 선택)
        → 입장료 차감
        → 자동 층계 판정 시작
            ┌─ 성공: 다음 층으로 진행, 보상 누적
            ├─ 실패: 누적 보상 전부 지급 (80%) 또는 현재 층 보상만 지급 (20%)
            ├─ 리셋: 마지막 리셋 층계로 복귀 (보상 유지)
            └─ 리셋 층계 도달: 누적 보상 즉시 지급 + 잭팟 판정
```

### 확률 구조

- 각 층마다 `success` / `fail` / `reset` 비율 설정 (합계 = 100)
- **리셋 이벤트**: 보상은 유지되고 마지막 리셋 층계 위치로만 복귀
- **리셋 층계 도달**: 누적된 보상 전체 즉시 지급 후 카운터 초기화

### 부스트 확률 적용 방식

```
deducted   = min(부스트%, rawFail%)
effSuccess = min(95%, rawSuccess% + deducted)
effFail    = rawFail% - (effSuccess - rawSuccess%)
effReset   = 100% - effSuccess% - effFail%
```

리셋 확률은 변하지 않고 실패 확률에서만 차감됩니다.

---

## 명령어

### 플레이어

| 명령어 | 설명 |
|--------|------|
| `/확률표` | 현재 있는 리전의 타워 층별 확률 표시 |
| `/확률표 <타워ID>` | 특정 타워의 층별 확률 표시 |

### 관리자 (`tt.luckytower.admin`)

| 명령어 | 설명 |
|--------|------|
| `/lt reload` | 설정 파일 전체 리로드 |
| `/lt stop <플레이어>` | 특정 플레이어의 게임 강제 종료 |
| `/lt info <타워ID>` | 타워 상세 정보 확인 |
| `/lt setblock <타워ID> <층번호>` | 현재 위치를 해당 층 레드스톤 램프로 등록 |
| `/lt start <그룹ID> <타워ID> <리전ID> <층수>` | 서 있는 위치 앞에 레드스톤 램프 + 버튼 자동 설치 |
| `/lt jackpot info <타워ID>` | 잭팟 현황 확인 |
| `/lt jackpot set-amount <타워ID> <금액>` | 잭팟 풀 금액 직접 설정 |
| `/lt jackpot sethologram <타워ID>` | 현재 위치를 잭팟 홀로그램 위치로 설정 |

### `/lt start` 자동 설치

명령어 실행 위치 기준:
- 플레이어가 바라보는 방향 2칸 앞 지면에 버튼 설치
- 버튼 뒤로 층수만큼 레드스톤 램프 수직으로 쌓임
- 타워 데이터 파일 (`towers/<타워ID>.yml`) 자동 생성

---

## Config 설정

`plugins/TT_LuckyTower/config.yml`

```yaml
settings:
  # 실패 시 전체 누적 보상 획득 확률 (나머지 % = 현재 층 보상만 지급)
  fail-full-reward-chance: 80
  # 층계 간 자동 판정 딜레이 (틱, 20틱 = 1초)
  floor-delay-ticks: 40
  # 게임 시작 전 카운트다운 (틱)
  start-delay-ticks: 60
```

---

## 타워별 YAML 구조

`plugins/TT_LuckyTower/towers/<타워ID>.yml`

```yaml
group: "group_a"          # 잭팟 공유 그룹 (생략 시 타워ID 사용)
region: "lucky_tower"     # WorldGuard 리전 이름
entry-fee-vault: 5000     # 입장료

interaction-block:        # 우클릭 상호작용 블록 좌표
  world: "world"
  x: 100
  y: 64
  z: 200

# 확률 부스트 아이템
boost-items:
  - material: IRON_BLOCK
    amount: 64
    boost: 5.0            # 성공 확률 +5% (실패 확률에서 차감)
  - material: QUARTZ_BLOCK
    amount: 32
    boost: 3.0

# 잭팟 설정
jackpot:
  enabled: true
  initial-amount: 50000.0          # 초기 잭팟 금액
  contribution-percent: 10.0       # 입장료 중 잭팟 풀 적립 비율
  hologram:
    world: "world"
    x: 100
    y: 66
    z: 200
  winner-rewards:
    commands:                       # 당첨자 1명 대상 명령어 ({player}, {amount})
      - "eco give {player} {amount}"
    region-commands:                # 리전 내 전원 대상 명령어 (인당 1회, {player}=각 플레이어)
      - "effect give {player} minecraft:glowing 30 0"
    region-messages:                # 리전 내 전원에게 보내는 메시지 ({player}=당첨자, {amount}=금액)
      - "§6★ [럭키타워 잭팟] §f{player}§e님이 §f{amount} §e잭팟을 터뜨렸습니다!"
    items:                          # 리전 내 전원에게 지급되는 아이템
      - material: DIAMOND
        amount: 3

# 층계 설정
floors:
  0:
    reset-floor: true     # 리셋 층계 (도달 시 누적 보상 지급)
    success: 85
    fail: 15
    reset: 0
    jackpot-chance: 0.0   # 잭팟 발동 확률 (리셋 층계만 적용)
    jackpot-payout: 0.0   # 잭팟 풀에서 지급 비율 (%)
    rewards:
      - type: VAULT
        amount: 500
    region-buffs: []
    lights: []            # 레드스톤 램프 블록 좌표 목록

  5:
    reset-floor: true
    success: 70
    fail: 20
    reset: 10
    jackpot-chance: 5.0
    jackpot-payout: 30.0
    rewards:
      - type: ITEM
        material: DIAMOND
        amount: 5
    region-buffs:
      - effect: SPEED
        amplifier: 1
        duration: 300       # 초
    lights: []

  14:
    reset-floor: false
    success: 30
    fail: 40
    reset: 30
    rewards:
      - type: MMOITEM
        mmoitem-type: SWORD
        mmoitem-id: LEGENDARY_SWORD
      - type: COMMAND
        command: "give {player} diamond 64"
    region-buffs:
      - effect: JUMP_BOOST
        amplifier: 2
        duration: 600
    lights: []
```

---

## 잭팟 시스템

```
[입장료 납부]
    → contribution-percent% 만큼 잭팟 풀에 적립
    → 잭팟_data.yml에 자동 저장

[리셋 층계 도달]
    → jackpot-chance% 확률로 판정
    → 당첨 시 잭팟 풀의 jackpot-payout% 지급

[당첨 연출]
    → 당첨자: 타이틀 표시 + Vault 금액 지급
    → winner-commands: 당첨자 대상 명령어 실행
    → region-commands: 리전 내 전원 대상 명령어 (인당 1회)
    → region-messages: 리전 내 전원에게 메시지 발송
    → items: 리전 내 전원에게 아이템 지급
    → 폭죽 사운드 3회 + 폭죽 엔티티 스폰
```

### 그룹 잭팟 공유

```yaml
# towers/tower_a.yml
group: "main_group"

# towers/tower_b.yml
group: "main_group"
```

같은 `group` 값을 가진 타워들은 잭팟 풀을 공유합니다. 홀로그램은 각 타워 위치에 개별 표시됩니다.

---

## 보상 타입

| 타입 | 필드 | 설명 |
|------|------|------|
| `VAULT` | `amount` | Vault 화폐 지급 |
| `ITEM` | `material`, `amount` | 바닐라 아이템 지급 |
| `MMOITEM` | `mmoitem-type`, `mmoitem-id` | MMOItems 아이템 지급 |
| `COMMAND` | `command` | 콘솔 명령어 실행 (`{player}` 치환) |

---

## 부스트 아이템

도전 시작 GUI에서 보유한 부스트 아이템 소모 여부를 선택할 수 있습니다.

- 해당 게임 1판 동안만 적용
- **실패 확률에서 차감** → 성공 확률 증가 (리셋 확률 불변)
- 여러 부스트 아이템의 효과 합산 적용
- 성공 확률 최대 95% 제한

---

## 빌드

```bash
# Windows (Gradle 8.10.2 기준)
gradlew.bat build

# 빌드 결과물
build/libs/TT_LuckyTower-1.0-SNAPSHOT.jar
```

### build.gradle 주요 의존성

```groovy
dependencies {
    compileOnly 'io.papermc.paper:paper-api:1.20.1-R0.1-SNAPSHOT'
    compileOnly 'com.sk89q.worldguard:worldguard-bukkit:7.0.9'
    compileOnly 'com.github.MilkBowl:VaultAPI:1.7.1'
    compileOnly 'com.github.decentsoftware-eu:decentholograms:2.8.9'
    compileOnly 'net.Indyuce:MMOItems:6.9.4'
    compileOnly 'io.lumine:MythicLib-dist:1.6-SNAPSHOT'
}
```

---

## 라이선스

이 플러그인은 개인 서버 용도로 자유롭게 사용할 수 있습니다.

- 이 플러그인을 서버에 설치하여 운영하는 것은 상업적 목적이어도 허용합니다.
- 단, 이 플러그인 자체를 이용한 후원자 혜택 제공은 금지합니다.
- 무단 재배포 및 2차 판매는 금지합니다.
