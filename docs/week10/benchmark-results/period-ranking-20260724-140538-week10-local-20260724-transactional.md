# Period Ranking Benchmark

Generated at: 2026-07-24T14:05:38.884235+09:00[Asia/Seoul]

## Environment

- Label: `week10-local-20260724-transactional`
- Java: `21.0.11`
- OS: `Mac OS X aarch64`
- Database: `MySQL 8.0.46`

## Claim boundary

- MySQL Testcontainers와 same-JVM JDBC 측정이며 운영 capacity 예측값이 아니다.
- strategy timer에는 현재 benchmark `run_key` cleanup, aggregation, shared `RankingScoreFormula`, staging score write가 포함된다. seed, digest, report I/O는 제외한다.
- legacy는 Spring Batch MySQL grouped paging SQL 형태를 재현한 비교군이며 현재 production 코드가 아니다. 각 page 조회+INSERT를 하나의 transaction으로 실행하고 source GROUP query를 다시 수행한다.
- snapshot은 별도 READ_COMMITTED transaction의 source GROUP query 1회로 staging을 고정한 뒤, 각 keyset page 조회+score UPDATE를 하나의 transaction으로 수행한다.
- contention은 snapshot INSERT SELECT transaction을 hold한 동안 동일 source row UPDATE 지연을 관찰한다. threshold 판정이나 운영 SLA 주장은 하지 않는다.
- chunk timer는 준비된 동일 형태 snapshot의 keyset paging, score 계산, UPDATE만 포함한다. snapshot INSERT와 cleanup은 제외하며 전체 Job 시간이 아니다.
- chunk 실행 순서는 run마다 회전하고 작은 표본의 중앙값만 제시한다.

## Median summary

### Strategy

| Products | Strategy | Median ms | Products/s | Source GROUP queries | Staging pages |
| ---: | --- | ---: | ---: | ---: | ---: |
| 1000 | legacy | 94.3083 | 10603.5169 | 11 | 10 |
| 1000 | snapshot | 208.4540 | 4797.2214 | 1 | 10 |
| 10000 | legacy | 3905.1711 | 2560.7072 | 101 | 100 |
| 10000 | snapshot | 1718.2828 | 5819.7639 | 1 | 100 |

### Snapshot chunk size

| Products | Chunk | Median ms | Products/s |
| ---: | ---: | ---: | ---: |
| 1000 | 100 | 186.1145 | 5373.0353 |
| 1000 | 500 | 120.0021 | 8333.1886 |
| 1000 | 1000 | 103.1666 | 9693.0611 |
| 10000 | 100 | 1540.8438 | 6489.9507 |
| 10000 | 500 | 1152.8405 | 8674.2265 |
| 10000 | 1000 | 1080.6385 | 9253.7887 |

### Isolation contention

| Isolation | Median snapshot insert ms | Median source UPDATE ms |
| --- | ---: | ---: |
| REPEATABLE_READ | 246.6640 | 421.3318 |
| READ_COMMITTED | 201.9055 | 4.5340 |

## Strategy raw rows

| Run | Products | Strategy | Rows | Elapsed ms | Products/s | GROUP queries | Pages | Digest |
| ---: | ---: | --- | ---: | ---: | ---: | ---: | ---: | --- |
| 1 | 1000 | legacy | 7000 | 101.0418 | 9896.8909 | 11 | 10 | `b4d85f394d570ba930d84cf6660019e32f0fa25dcd04cb4654a5ab044011b235` |
| 1 | 1000 | snapshot | 7000 | 172.4740 | 5797.9739 | 1 | 10 | `b4d85f394d570ba930d84cf6660019e32f0fa25dcd04cb4654a5ab044011b235` |
| 2 | 1000 | snapshot | 7000 | 197.0004 | 5076.1325 | 1 | 10 | `b4d85f394d570ba930d84cf6660019e32f0fa25dcd04cb4654a5ab044011b235` |
| 2 | 1000 | legacy | 7000 | 185.5143 | 5390.4203 | 11 | 10 | `b4d85f394d570ba930d84cf6660019e32f0fa25dcd04cb4654a5ab044011b235` |
| 3 | 1000 | legacy | 7000 | 94.3083 | 10603.5169 | 11 | 10 | `b4d85f394d570ba930d84cf6660019e32f0fa25dcd04cb4654a5ab044011b235` |
| 3 | 1000 | snapshot | 7000 | 232.3912 | 4303.0888 | 1 | 10 | `b4d85f394d570ba930d84cf6660019e32f0fa25dcd04cb4654a5ab044011b235` |
| 4 | 1000 | snapshot | 7000 | 208.4540 | 4797.2214 | 1 | 10 | `b4d85f394d570ba930d84cf6660019e32f0fa25dcd04cb4654a5ab044011b235` |
| 4 | 1000 | legacy | 7000 | 91.6523 | 10910.8020 | 11 | 10 | `b4d85f394d570ba930d84cf6660019e32f0fa25dcd04cb4654a5ab044011b235` |
| 5 | 1000 | legacy | 7000 | 90.1315 | 11094.9054 | 11 | 10 | `b4d85f394d570ba930d84cf6660019e32f0fa25dcd04cb4654a5ab044011b235` |
| 5 | 1000 | snapshot | 7000 | 214.6734 | 4658.2395 | 1 | 10 | `b4d85f394d570ba930d84cf6660019e32f0fa25dcd04cb4654a5ab044011b235` |
| 1 | 10000 | legacy | 70000 | 3890.0477 | 2570.6626 | 101 | 100 | `81c9ed1084d153c3a2e3062d1c84065db248f51521455f290b7398737aacf67c` |
| 1 | 10000 | snapshot | 70000 | 1674.8059 | 5970.8411 | 1 | 100 | `81c9ed1084d153c3a2e3062d1c84065db248f51521455f290b7398737aacf67c` |
| 2 | 10000 | snapshot | 70000 | 1522.0438 | 6570.1134 | 1 | 100 | `81c9ed1084d153c3a2e3062d1c84065db248f51521455f290b7398737aacf67c` |
| 2 | 10000 | legacy | 70000 | 3905.1711 | 2560.7072 | 101 | 100 | `81c9ed1084d153c3a2e3062d1c84065db248f51521455f290b7398737aacf67c` |
| 3 | 10000 | legacy | 70000 | 3927.4068 | 2546.2094 | 101 | 100 | `81c9ed1084d153c3a2e3062d1c84065db248f51521455f290b7398737aacf67c` |
| 3 | 10000 | snapshot | 70000 | 1718.2828 | 5819.7639 | 1 | 100 | `81c9ed1084d153c3a2e3062d1c84065db248f51521455f290b7398737aacf67c` |
| 4 | 10000 | snapshot | 70000 | 1743.4509 | 5735.7508 | 1 | 100 | `81c9ed1084d153c3a2e3062d1c84065db248f51521455f290b7398737aacf67c` |
| 4 | 10000 | legacy | 70000 | 3988.4456 | 2507.2424 | 101 | 100 | `81c9ed1084d153c3a2e3062d1c84065db248f51521455f290b7398737aacf67c` |
| 5 | 10000 | legacy | 70000 | 3833.1883 | 2608.7944 | 101 | 100 | `81c9ed1084d153c3a2e3062d1c84065db248f51521455f290b7398737aacf67c` |
| 5 | 10000 | snapshot | 70000 | 1807.2992 | 5533.1183 | 1 | 100 | `81c9ed1084d153c3a2e3062d1c84065db248f51521455f290b7398737aacf67c` |

## Chunk raw rows

| Run | Products | Chunk | Elapsed ms | Products/s | Pages | Digest |
| ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 1 | 1000 | 100 | 186.1145 | 5373.0353 | 10 | `b4d85f394d570ba930d84cf6660019e32f0fa25dcd04cb4654a5ab044011b235` |
| 1 | 1000 | 500 | 110.1608 | 9077.6365 | 2 | `b4d85f394d570ba930d84cf6660019e32f0fa25dcd04cb4654a5ab044011b235` |
| 1 | 1000 | 1000 | 111.3707 | 8979.0217 | 1 | `b4d85f394d570ba930d84cf6660019e32f0fa25dcd04cb4654a5ab044011b235` |
| 2 | 1000 | 500 | 205.2460 | 4872.2021 | 2 | `b4d85f394d570ba930d84cf6660019e32f0fa25dcd04cb4654a5ab044011b235` |
| 2 | 1000 | 1000 | 102.1963 | 9785.0949 | 1 | `b4d85f394d570ba930d84cf6660019e32f0fa25dcd04cb4654a5ab044011b235` |
| 2 | 1000 | 100 | 169.0919 | 5913.9447 | 10 | `b4d85f394d570ba930d84cf6660019e32f0fa25dcd04cb4654a5ab044011b235` |
| 3 | 1000 | 1000 | 101.9773 | 9806.1007 | 1 | `b4d85f394d570ba930d84cf6660019e32f0fa25dcd04cb4654a5ab044011b235` |
| 3 | 1000 | 100 | 247.0126 | 4048.3761 | 10 | `b4d85f394d570ba930d84cf6660019e32f0fa25dcd04cb4654a5ab044011b235` |
| 3 | 1000 | 500 | 109.7895 | 9108.3425 | 2 | `b4d85f394d570ba930d84cf6660019e32f0fa25dcd04cb4654a5ab044011b235` |
| 4 | 1000 | 100 | 178.8696 | 5590.6641 | 10 | `b4d85f394d570ba930d84cf6660019e32f0fa25dcd04cb4654a5ab044011b235` |
| 4 | 1000 | 500 | 121.3142 | 8243.0576 | 2 | `b4d85f394d570ba930d84cf6660019e32f0fa25dcd04cb4654a5ab044011b235` |
| 4 | 1000 | 1000 | 114.9701 | 8697.9117 | 1 | `b4d85f394d570ba930d84cf6660019e32f0fa25dcd04cb4654a5ab044011b235` |
| 5 | 1000 | 500 | 120.0021 | 8333.1886 | 2 | `b4d85f394d570ba930d84cf6660019e32f0fa25dcd04cb4654a5ab044011b235` |
| 5 | 1000 | 1000 | 103.1666 | 9693.0611 | 1 | `b4d85f394d570ba930d84cf6660019e32f0fa25dcd04cb4654a5ab044011b235` |
| 5 | 1000 | 100 | 257.3133 | 3886.3137 | 10 | `b4d85f394d570ba930d84cf6660019e32f0fa25dcd04cb4654a5ab044011b235` |
| 1 | 10000 | 100 | 1315.3229 | 7602.6958 | 100 | `81c9ed1084d153c3a2e3062d1c84065db248f51521455f290b7398737aacf67c` |
| 1 | 10000 | 500 | 1108.5880 | 9020.4841 | 20 | `81c9ed1084d153c3a2e3062d1c84065db248f51521455f290b7398737aacf67c` |
| 1 | 10000 | 1000 | 1080.6385 | 9253.7887 | 10 | `81c9ed1084d153c3a2e3062d1c84065db248f51521455f290b7398737aacf67c` |
| 2 | 10000 | 500 | 1152.8405 | 8674.2265 | 20 | `81c9ed1084d153c3a2e3062d1c84065db248f51521455f290b7398737aacf67c` |
| 2 | 10000 | 1000 | 1069.2186 | 9352.6246 | 10 | `81c9ed1084d153c3a2e3062d1c84065db248f51521455f290b7398737aacf67c` |
| 2 | 10000 | 100 | 1802.2113 | 5548.7388 | 100 | `81c9ed1084d153c3a2e3062d1c84065db248f51521455f290b7398737aacf67c` |
| 3 | 10000 | 1000 | 1285.7121 | 7777.7911 | 10 | `81c9ed1084d153c3a2e3062d1c84065db248f51521455f290b7398737aacf67c` |
| 3 | 10000 | 100 | 1540.8438 | 6489.9507 | 100 | `81c9ed1084d153c3a2e3062d1c84065db248f51521455f290b7398737aacf67c` |
| 3 | 10000 | 500 | 1153.8251 | 8666.8249 | 20 | `81c9ed1084d153c3a2e3062d1c84065db248f51521455f290b7398737aacf67c` |
| 4 | 10000 | 100 | 1690.4788 | 5915.4839 | 100 | `81c9ed1084d153c3a2e3062d1c84065db248f51521455f290b7398737aacf67c` |
| 4 | 10000 | 500 | 1118.8556 | 8937.7036 | 20 | `81c9ed1084d153c3a2e3062d1c84065db248f51521455f290b7398737aacf67c` |
| 4 | 10000 | 1000 | 1073.9853 | 9311.1145 | 10 | `81c9ed1084d153c3a2e3062d1c84065db248f51521455f290b7398737aacf67c` |
| 5 | 10000 | 500 | 1178.6730 | 8484.1176 | 20 | `81c9ed1084d153c3a2e3062d1c84065db248f51521455f290b7398737aacf67c` |
| 5 | 10000 | 1000 | 1084.1693 | 9223.6514 | 10 | `81c9ed1084d153c3a2e3062d1c84065db248f51521455f290b7398737aacf67c` |
| 5 | 10000 | 100 | 1404.3673 | 7120.6443 | 100 | `81c9ed1084d153c3a2e3062d1c84065db248f51521455f290b7398737aacf67c` |

## Contention raw rows

| Run | Isolation | Hold ms | Snapshot insert ms | Source UPDATE ms | Updated rows |
| ---: | --- | ---: | ---: | ---: | ---: |
| 1 | REPEATABLE_READ | 400 | 588.2736 | 420.0935 | 1 |
| 1 | READ_COMMITTED | 400 | 527.6442 | 5.0322 | 1 |
| 2 | READ_COMMITTED | 400 | 127.6773 | 2.7395 | 1 |
| 2 | REPEATABLE_READ | 400 | 196.4421 | 425.8960 | 1 |
| 3 | REPEATABLE_READ | 400 | 246.6640 | 412.5262 | 1 |
| 3 | READ_COMMITTED | 400 | 115.6440 | 103.2317 | 1 |
| 4 | READ_COMMITTED | 400 | 201.9055 | 4.5340 | 1 |
| 4 | REPEATABLE_READ | 400 | 144.0996 | 422.5543 | 1 |
| 5 | REPEATABLE_READ | 400 | 249.4748 | 421.3318 | 1 |
| 5 | READ_COMMITTED | 400 | 508.4644 | 2.7277 | 1 |
