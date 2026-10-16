# TODO

## 항상 지킬 것

<!-- 사용자가 한 번 말한 금지·필수 사항. 매 턴 읽는다. 지우지 않는다 -->

## 진행 중
- [ ] #38 (10-16 09:40) 기존 6개가 그대로인지 diff 로 보여주고, 전체 테스트 돌려서 green

## 완료
- [x] #37 (10-16 09:25) 오늘 바꿀 코드의 안전망을 다시 점검해줘. JaCoCo 리포트로 processApproval 에서 (사유: 파일 approvalapicharacterizationtest.java)
- [x] #36 (10-16 09:19) 커밋해주세요 (사유: B02 커밋, origin/main 일치)
- [x] #35 (10-16 09:17) B02 진행해줘 (사유: 파일 mailsender.java)
- [x] #34 (10-16 09:14) hi (사유: 인사 - 상태 확인 후 답변)
- [x] #33 (10-15 17:28) 리드미 리팩토링 이력이랑 백로그 업데이트 해주고 커밋해줘. (사유: 파일 readme.md)
- [x] #32.4 (10-15 17:16) 필드 repo·userRepo도 rename (approvalRepository·userRepository) (사유: 352c127 에서 이미 approvalRepository·userRepository 로 rename 완료, 남은 사용처 0건)
- [x] #32.3 (10-15 17:16) 외부 시그니처·동작은 그대로 유지 (사유: 특성화 20개 본문 무수정 green, 메일 문구·감사 로그 행위명 HEAD 와 동일, public 시그니처 유지)
- [x] #32.2 (10-15 17:16) 각 분기를 submit/approve/reject/cancel private 메서드로 추출 (사유: 파일 approvalservice.java)
- [x] #32.1 (10-15 17:16) 초기 null·권한 체크를 guard clause로 빼서 조기 반환 (사유: 파일 approvalservice.java)
- [x] #31 (10-15 17:16) 커밋해주세요 (사유: 남은 변경 TODO.md 커밋, origin/main 일치)
- [x] #30 (10-15 17:08) 실행하는 캡쳐 이미지도 있잫아 그것도 리드미에 반영해서 설명이 쉽계 만들어 줘 (사유: 파일 readme.md)
- [x] #29 (10-15 17:07) 지금까지 내용을 기반으로 깃허브 리드미 자세히 작성해서 올려줘 (사유: 파일 readme.md)
- [x] #28 (10-15 17:06) 커밋 하면 푸시하고 있는 거지? (사유: post-commit 훅 확인, HEAD 와 origin/main 일치 확인)
- [x] #27 (10-15 16:52) 이름개선·enum·statusLabel 정리 후 기존 특성화 테스트가 그대로 (사유: 기존 ApprovalServiceTest 6개 무수정 green (전체 20 green, 16:52))
- [x] #26 (10-15 16:47) 문서 반영하고 커밋해줘 (사유: 파일 특성화 테스트 결과.md)
- [x] #25 (10-15 16:44) 특성화 추가 하고 그린 확인 해줘 (사유: 파일 approvalservicebranchcharacterizationtest.java)
- [x] #24 (10-15 16:43) 나눠서 커밋해줘 (사유: enum 전환·라벨 위임 커밋 2개로 분리, 각 단계 green, origin/main 일치)
- [x] #23 (10-15 16:41) statusLabel의 tmp 임시변수와 5분기 if를 없애줘. enum이 라벨을 소유하게 (사유: 파일 approvalstatus.java)
- [x] #21 (10-15 16:32) status int를 ApprovalStatus enum으로 전환. (사유: 파일 approvalstatus.java)
- [x] #20 (10-15 16:29) 커밋해줘 (사유: ApprovalService rename 커밋, origin/main 일치)
- [x] #19 (10-15 16:27) 요청은 예시 입니다. 그 외의 약어는 지금 어프로드서비스 내에서는 다 수정 해주세요. (사유: 파일 approvalservice.java)
- [x] #18 (10-15 16:25) 약어 지역변수를 의미 식별자로 rename. d→approval, u→user, s→status, (사유: 파일 approvalservice.java)
- [x] #17 (10-15 16:23) status 매직넘버(0/1/2/3/9) 사용처와 약어 변수(d/u/s/proc), (사유: grep 으로 status 숫자·약어 변수 위치 전수 조사해 답변)
- [x] #16 (10-15 16:23) 커밋해줘 (사유: TODO.md 진행 기록 커밋, origin/main 일치)
- [x] #15 (10-15 16:18) green이면 테스트와 docs/특성화 테스트 결과.md를 'test: ApprovalService 특성화 (사유: 6개 green 확인 후 커밋, origin/main 과 HEAD 일치)
- [x] #14 (10-15 16:14) processApproval 테스트 커버리지를 알려주고 안 덮인 분기를 docs/특성화 테스트 결과.md (사유: 파일 특성화 테스트 결과.md)
- [x] #11 (10-15 16:07) 방금 만든 특성화 테스트 6개를 실행해줘 (사유: ApprovalServiceTest 6개 실행 green)
- [x] #10 (10-15 16:03) 리팩토링 전 안전망. processApproval(4인자)의 현재 동작을 @DataJpaTest (사유: 파일 approvalservicetest.java)
- [x] #7 (10-15 15:34) 지금 레거시 포털을 소나큐브로 분석해줘. (사유: 분석 ANALYSIS SUCCESSFUL·CE 처리 SUCCESS 확인, 지표·이슈 37건 조회해 보고함)
- [x] #6 (10-15 15:27) SonarQube Community를 이 프로젝트 밖 D:\sonarqube 에 파일을 넣어 놨습니다. (사유: D:\sonarqube 26.6 압축해제·한글팩 배포 확인, Java21 로 구동 UP, legacy-portal 분석 완료)
- [x] #5 (10-15 15:26) 커밋 해줘 (사유: 파일 .gitattributes)
- [x] #4 (10-15 15:23) 커밋 해줘 (사유: 07·08 문서와 투두가드 세팅 커밋 2개, 원격 푸시 확인)
- [x] #3 (10-15 15:18) 스코어링을 바탕으로 우선순위 순 리팩토링 (사유: 파일 08-리팩토링-백로그.md)
- [x] #2 (10-15 15:14) 각 스멜을 영향도 × 수정난이도로 채점해 (사유: 파일 07-심각도-점수.md)

## 보류 (사용자 확인 필요)
