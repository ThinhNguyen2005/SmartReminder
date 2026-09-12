# G3 - Group Tasks dung that

## Goal
Bien phan task dang khoa trong Group Detail thanh luong `task list -> task detail/editor -> task actions`, voi Supabase la authority, Room la cache doc offline, va khong kich hoat replay/optimistic queue truoc G4.

## Scope decisions

- G3 gom create/view/edit task, mot assignee, deadline tuyet doi, 1-5 reminder offsets, start/complete/reassign/cancel/reopen.
- Moi mutation gui online trong G3; khi mat mang tra feedback `NetworkRequired`. Schema pending command G1 duoc giu nguyen nhung replay/projection thuoc G4.
- `expectedVersion` bat buoc cho moi mutation tren task da ton tai; conflict khong auto-merge.
- Reminder offsets la cau hinh cua task. Standalone `GroupReminder`, delivery scheduler, outbox consumer va FCM thuoc G5.
- UI/ViewModel chi dung `CollaborationRepository`; actor lay tu repository boundary, server van tai kiem tra quyen.
- Migration/RPC chi apply va test tren Supabase development project; credential khong commit.

## Tasks

- [ ] 1. Hoan thien task command contract va policy table tests: them typed commands cho edit day du, reassign, start, complete, cancel, reopen; validate title, assignee, future/past absolute deadline duoc phep, 1-5 offset duong/khong trung, va `expectedVersion >= 0`. Verify: focused pure JVM tests RED -> GREEN.
- [ ] 2. Them Supabase G3 RPC/RLS transaction-safe: create idempotent bang client task ID; lock task row khi mutate; kiem tra membership/permission/state/version; cap nhat version atomically; thay offsets trong cung transaction; tra typed envelope `APPLIED/CONFLICT/NOT_AUTHORIZED/NOT_FOUND/INVALID_STATE`. Verify: SQL static checks va matrix integration 2 tai khoan tren dev project.
- [ ] 3. Hoan thien Room task cache khong pha du lieu: DAO replace/upsert/delete theo group/task, relation offsets ordering on dinh, mapper cache-domain; chi bump schema neu column/index hien tai khong du. Verify: DAO instrumentation va migration preservation neu co bump.
- [ ] 4. Them remote DTO/mapper/data source: PostgREST reads cho tasks + offsets, RPC writes cho tung command, typed error mapping khong parse chuoi o UI. Verify: mapper/envelope tests va remote contract tests.
- [ ] 5. Mo rong `DefaultCollaborationRepository`: observe cache-first, `refreshTasks(groupId)`, refresh cache sau mutation thanh cong/conflict can refresh, giu cache khi network loi, mutation offline tra `NetworkRequired`; khong ghi pending queue trong G3. Verify: repository unit/integration tests.
- [ ] 6. Them presentation rieng cho task trong feature Groups: task list/detail/editor state, permissions tu `GroupTaskPolicy`, deterministic `Clock`, validation inline, mutation feedback an toan, process restore task/group ID. Verify: ViewModel tests cho loading/empty/offline/error/conflict va moi state transition.
- [ ] 7. Trien khai Compose dung that: thay G3 lock bang task rows co assignee/deadline/status/overdue text + icon, editor chon member/deadline/reminder offsets, detail actions gated, confirm cancel/reopen, Snackbar/inline feedback, EN/VI, dark theme, TalkBack live region, 48dp, 200% font scale. Sample chi trong `@Preview`. Verify: Compose tests va manual inspection.
- [ ] 8. Wiring tai composition root, security/E2E va zero-churn review. Verify: full JVM suite, compile, assemble, Room instrumentation, SQL/RLS smoke voi hai account, install Debug va thao tac tren thiet bi; review doc ro phan chua duoc test live.

## Commit and review gates

- Moi task co commit rieng sau RED/GREEN va review doc lap; task phu thuoc chi bat dau khi finding Critical/Important da dong.
- Khong commit `local.properties`, key, `.superpowers/` preview artifacts hoac thay doi khong lien quan.
- Preflight cuoi: `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:compileDebugAndroidTestKotlin`, instrumentation tren thiet bi, `git diff --check`.

## Done when

- Hai thanh vien nhin cung du lieu task sau refresh; cache van doc duoc offline.
- Permission/state/version duoc kiem tra ca client policy va RPC; conflict hien thong diep co huong xu ly.
- Khong co queue replay, optimistic projection, standalone reminder hay FCM trong diff G3.
