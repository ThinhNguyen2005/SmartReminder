# Cue

> **AI Reminder & Schedule — biến việc cần làm thành một lịch trình thực tế và nhắc người dùng vào thời điểm phù hợp nhất.**

Cue là ứng dụng Android giúp người dùng quản lý **công việc cá nhân, lịch trình, routine, công việc nhóm và reminder**, đồng thời sử dụng AI để hỗ trợ lập kế hoạch, hiểu yêu cầu bằng ngôn ngữ tự nhiên và đề xuất thời điểm thực hiện công việc hợp lý.

README này tập trung vào **định hướng sản phẩm**, nhằm giúp tất cả thành viên trong team hiểu thống nhất:

* Chúng ta đang xây app gì?
* Cue giải quyết vấn đề gì?
* Feature nào là core?
* AI có vai trò gì?
* Những gì V1 cần và không cần làm?
* UX của sản phẩm phải hướng tới điều gì?
* Khi phát triển feature mới cần dựa trên nguyên tắc nào?

---

# 1. Product Vision

Các ứng dụng Todo thông thường chủ yếu trả lời:

> **“Tôi cần làm việc gì?”**

Các ứng dụng Calendar trả lời:

> **“Tôi có lịch gì vào thời gian nào?”**

Các ứng dụng Reminder trả lời:

> **“Khi nào cần báo cho tôi?”**

Cue muốn kết hợp cả ba và tiến thêm một bước:

> **“Tôi cần làm gì, nên làm khi nào, và khi nào là thời điểm phù hợp để nhắc tôi?”**

Mục tiêu cuối cùng của Cue không phải tạo thêm một Todo App.

Cue hướng tới trở thành:

> **Personal AI Scheduler — trợ lý quản lý lịch trình và công việc cá nhân.**

---

# 2. Vấn đề Cue giải quyết

Người dùng hiện nay thường phải sử dụng nhiều ứng dụng riêng:

```text
Calendar
   ↓
Lịch học / meeting

Todo App
   ↓
Việc cần làm

Reminder
   ↓
Nhắc việc

Messenger
   ↓
Việc nhóm

AI
   ↓
Hỏi cách sắp xếp
```

Dữ liệu bị tách rời.

Ví dụ người dùng có:

```text
09:00    Đi học

14:00    Meeting nhóm

23:59    Deadline Assignment
```

Todo App chỉ biết:

> Assignment chưa hoàn thành.

Calendar chỉ biết:

> 14:00 có meeting.

Nhưng không hệ thống nào chủ động nói:

> Bạn có khoảng trống từ 15:00–17:30 và Assignment cần khoảng 2 giờ. Đây là thời điểm phù hợp để bắt đầu.

Cue được xây dựng để giải quyết khoảng trống đó.

---

# 3. Core Concept

Kiến trúc sản phẩm của Cue xoay quanh 5 khái niệm:

```text
TASK
+
SCHEDULE
+
REMINDER
+
GROUP
+
AI
```

Trong đó:

```text
Task
→ Việc cần làm

Schedule
→ Khi nào / trong routine nào việc diễn ra

Reminder
→ Khi nào cần nhắc

Group
→ Công việc và lịch trình có nhiều người

AI
→ Hiểu, tổ chức và đề xuất
```

---

# 4. Task — Công việc

Task là đơn vị công việc cơ bản nhất trong Cue.

Ví dụ:

```text
Nộp Assignment Android
```

Task có thể chứa:

* Title
* Description
* Priority
* Deadline
* Status
* Estimated duration
* Schedule
* Reminder
* Group
* Assignee

Ví dụ:

```text
Nộp Assignment Android

Priority
High

Deadline
Friday · 23:59

Estimated duration
2 hours

Schedule
🎓 University
```

Task có thể là:

```text
Personal Task
```

hoặc:

```text
Group Task
```

---

# 5. Today — Trung tâm của ứng dụng

Màn hình **Today** là màn hình quan trọng nhất của Cue.

Người dùng mở app lên phải trả lời được ngay:

> **Hôm nay tôi cần làm gì?**

Ví dụ:

```text
Good morning 👋

Tuesday · August 18

4 / 7 completed
████████████░░░░░


UP NEXT

09:00
○ Nộp bài Android

14:00
○ Meeting nhóm

17:30
○ Workout

20:00
○ TOEIC
```

Today tổng hợp dữ liệu từ:

```text
Personal Tasks
+
Schedules
+
Group Tasks
+
Deadlines
+
AI Suggestions
```

Today không chỉ là danh sách Todo.

Nó là **daily command center** của người dùng.

---

# 6. Schedule — Lịch trình

Schedule giúp nhóm nhiều hoạt động thành một lịch trình có ý nghĩa.

Ví dụ:

```text
🎓 University

Monday
Wednesday
Friday
```

Timeline:

```text
06:30
Wake up

07:00
Breakfast

07:20
Bring laptop

07:30
Leave home

08:00
Class
```

Schedule có thể đại diện cho:

```text
🎓 Study Day

💼 Work Day

🏋️ Workout

🌙 Night Routine

☀️ Morning Routine

📚 Study Session
```

---

# 7. Schedule không phải Folder

Schedule phải có logic riêng.

Ví dụ:

```text
🎓 University

Active
Mon · Wed · Fri

07:00 → 13:00
```

Các task bên trong có thể inherit schedule này.

Ví dụ:

```text
University
│
├── Bring laptop
│      07:20
│
├── Review lesson
│      07:40
│
└── Submit assignment
       09:00
```

Không cần khai báo `Mon/Wed/Fri` cho từng task.

---

# 8. Skip Today / Run Today

Schedule cần linh hoạt với đời sống thực tế.

Ví dụ:

```text
University
Mon · Wed · Fri
```

Nhưng hôm nay nghỉ học.

Người dùng chọn:

```text
Skip Today
```

Lịch hôm nay không chạy.

Những ngày sau vẫn bình thường.

Ngược lại, nếu hôm nay học bù:

```text
Run Today
```

Cue chạy Schedule cho riêng ngày hôm nay mà không sửa cấu hình gốc.

---

# 9. Routine

Schedule cũng có thể hoạt động như routine.

Ví dụ:

## Night Routine

```text
21:30
○ Clean desk

22:00
○ Prepare clothes

22:15
○ Review tomorrow

23:00
○ Sleep
```

Cue hiển thị progress:

```text
Night Routine

3 / 4 completed

✓ Clean desk
✓ Prepare clothes
✓ Review tomorrow
○ Sleep
```

---

# 10. Reminder Engine

Reminder là một trong những core system quan trọng nhất của Cue.

Một reminder cơ bản:

```text
20:00
→ Study Kotlin
```

Nhưng Cue cần hỗ trợ nhiều dạng hơn.

---

## Time Reminder

```text
Monday · 20:00

Study Kotlin
```

---

## Repeating Reminder

```text
Every
Mon · Wed · Fri

20:00
```

---

## Deadline Reminder

Ví dụ:

```text
Deadline
Friday · 23:59
```

Có thể nhắc:

```text
24 hours before

3 hours before

30 minutes before
```

---

# 11. Smart Snooze

Notification phải cho phép xử lý nhanh.

Ví dụ:

```text
🔔 Study Kotlin

[ Done ]

[ +15 min ]

[ +1 hour ]

[ Tonight ]
```

Người dùng không nên phải mở app chỉ để trì hoãn reminder.

---

# 12. Actionable Notification

Notification nên là một phần của workflow.

Ví dụ:

```text
🎓 University

Nộp Assignment Android

Deadline còn 2 giờ.

[ Complete ]

[ Snooze ]
```

Hoàn thành task trực tiếp từ notification.

---

# 13. Group — Nhóm làm việc

Cue hỗ trợ Group để nhiều người quản lý công việc chung.

Ví dụ:

```text
👥 Android Project

Members

Minh
Lan
Huy
You
```

Trong nhóm:

```text
○ Login Screen
   Minh

○ Database
   You

○ Presentation
   Lan

○ Testing
   Huy
```

---

# 14. Assign Task

Một thành viên có thể giao task cho thành viên khác.

Ví dụ:

```text
Task

Implement Login Screen

Assign to
Minh

Deadline
Tomorrow · 20:00
```

Minh nhận:

```text
👥 Android Project

You have a new task

Implement Login Screen

Deadline
Tomorrow · 20:00
```

Task sau đó xuất hiện trong:

```text
Today
```

của Minh.

---

# 15. Group Reminder

Group có thể có reminder chung.

Ví dụ:

```text
Weekly Meeting

Wednesday
20:00
```

Tất cả thành viên nhận:

```text
Android Project

Meeting starts in 30 minutes.
```

Leader cũng có thể gửi reminder liên quan tới project.

---

# 16. Group không phải Chat App

Cue **không hướng tới cạnh tranh với Messenger, Discord hay Slack**.

Group tồn tại để phục vụ:

```text
Task
Schedule
Deadline
Reminder
```

Không phải để tạo social network.

V1 không cần:

* voice call;
* video call;
* social feed;
* sticker;
* hệ thống chat phức tạp.

Nếu sau này có chat, chat chỉ là feature phụ phục vụ công việc.

---

# 17. Vai trò của AI

AI là điểm khác biệt lớn của Cue.

Tuy nhiên nguyên tắc quan trọng:

> **AI không được trở thành một chatbot đứng riêng trong app.**

Không thiết kế:

```text
Home
Calendar
Task
AI Chat
Profile
```

AI phải được tích hợp trực tiếp vào workflow.

---

# 18. AI Natural Language Task

User có thể nhập:

> Mai lúc 8h nhắc tôi mang laptop đi học.

AI parse thành:

```text
Task
Mang laptop

Date
Tomorrow

Time
08:00

Schedule
University
```

Sau đó hiển thị preview:

```text
AI understood

Mang laptop

Tomorrow · 08:00

🎓 University

[ Create ]
```

AI không tự tạo dữ liệu quan trọng mà không cho user kiểm tra.

---

# 19. AI Schedule Generator

Người dùng có thể nói:

> Tôi học thứ 2, 4, 6 lúc 8h. Thường thức lúc 6h30 và rời nhà lúc 7h30.

AI đề xuất:

```text
🎓 University

Mon · Wed · Fri


06:30
Wake up

07:00
Breakfast

07:20
Prepare

07:30
Leave

08:00
Class
```

User chọn:

```text
Apply
```

---

# 20. AI Smart Reminder

AI không chỉ tạo reminder.

AI có thể giúp quyết định:

> **Nên nhắc vào lúc nào?**

Ví dụ:

```text
Assignment

Deadline
23:59

Estimated work
2 hours
```

Lịch người dùng:

```text
14:00
Meeting

18:00
Gym
```

Khoảng trống:

```text
15:00 → 17:30
```

AI có thể đề xuất:

```text
✨ Cue Suggestion

Assignment cần khoảng 2 giờ.

Bạn đang có thời gian trống
15:00 → 17:30.

Bắt đầu lúc 15:00?

[ Add to plan ]

[ Ignore ]
```

---

# 21. AI Conflict Detection

AI có thể phát hiện lịch quá tải.

Ví dụ:

```text
14:00 TOEIC
15:00 Gym
16:00 Kotlin
```

AI đề xuất:

```text
Schedule looks crowded.

Move Kotlin
→ 20:00

[ Apply ]
```

Nguyên tắc:

> **AI đề xuất, user quyết định.**

AI không tự ý sửa lịch.

---

# 22. AI Prioritization

Nếu user có:

```text
○ Watch tutorial

○ Submit assignment

○ Buy milk

○ Review Kotlin

○ Team meeting
```

AI có thể đề xuất:

```text
Suggested priority

1. Submit assignment
2. Team meeting
3. Review Kotlin
4. Buy milk
5. Watch tutorial
```

Có thể dựa trên:

* deadline;
* priority;
* estimated duration;
* schedule;
* group task;
* workload.

---

# 23. AI Morning Planning

Buổi sáng:

```text
☀️ Good morning

You have 6 tasks today.


Important

09:00
Submit Assignment

14:00
Android Project Meeting


Suggested plan

07:30 → 08:30
Review Assignment

10:00 → 11:00
Study Kotlin

19:30 → 20:30
TOEIC
```

User có thể:

```text
Apply Plan
```

---

# 24. AI Evening Review

Cuối ngày:

```text
🌙 Daily Review

Completed
5 / 7

Remaining

○ Kotlin
○ TOEIC
```

AI đề xuất:

```text
Move Kotlin
→ Tomorrow · 19:00

Move TOEIC
→ Tomorrow · 20:30
```

User quyết định có áp dụng hay không.

---

# 25. User Profile & Personal Time

Cue cần hiểu một số preference cơ bản của người dùng.

Ví dụ:

```text
Wake time
07:00

Sleep time
23:30

Preferred study period
19:00 → 22:00

Default reminder
30 minutes before
```

Thông tin này có thể giúp AI đưa ra suggestion phù hợp hơn.

---

# 26. Calendar

Calendar giúp user xem công việc theo ngày.

```text
AUGUST

M  T  W  T  F  S  S

               1  2
3  4  5  6  7  8  9
...
```

Chọn ngày:

```text
Wednesday · Aug 20


08:00
University

10:00
Android Assignment

14:00
Team Meeting

19:30
TOEIC
```

Calendar phải tổng hợp:

```text
Schedule
+
Task
+
Group
+
AI Plan
```

---

# 27. Widget

Cue nên có Home Screen Widget.

Ví dụ:

```text
┌─────────────────────────┐
│ TODAY              4/7 │
│                         │
│ ○ 14:00 Meeting         │
│ ○ 18:00 Gym             │
│ ○ 20:00 Kotlin          │
│                         │
│ Next · 37 min           │
└─────────────────────────┘
```

Widget nên hỗ trợ:

* xem task;
* complete nhanh;
* xem next event;
* xem progress.

---

# 28. Notification Inbox

Cue có thể có một Inbox nội bộ.

Ví dụ:

```text
INBOX


✨ AI

Assignment nên bắt đầu lúc 15:00


👥 Android Project

Minh assigned you a task


🔔 Reminder

TOEIC · 20:00


⚠ Deadline

Assignment due in 3 hours
```

Đây là nơi tổng hợp các sự kiện quan trọng.

---

# 29. Navigation Direction

Bottom Navigation dự kiến:

```text
Today

Calendar

Schedules

Groups

Profile
```

Task không nhất thiết cần tab riêng.

Task được xuất hiện trong:

```text
Today
Schedule
Calendar
Group
```

AI cũng không cần tab riêng.

---

# 30. AI Placement

AI phải xuất hiện theo ngữ cảnh.

```text
Create Task
   ↓
AI parse

Create Schedule
   ↓
AI generate

Today
   ↓
AI planning

Calendar
   ↓
AI optimize

Reminder
   ↓
AI suggest timing

Group
   ↓
AI summarize / prioritize
```

Điều này giúp AI thực sự trở thành **intelligence layer** của Cue.

---

# 31. Product Structure

Có thể hình dung Cue như sau:

```text
                     CUE

                      │
          ┌───────────┴───────────┐
          │                       │
      PERSONAL                  GROUP
          │                       │
      ┌───┴────┐             ┌────┴────┐
      │        │             │         │
    TASK    SCHEDULE       TASK    SCHEDULE
      │        │             │         │
      └────────┴──────┬──────┴─────────┘
                      │
               REMINDER ENGINE
                      │
                  AI ENGINE
                      │
                NOTIFICATION
```

---

# 32. V1 Scope

V1 cần tập trung vào những feature tạo nên core product.

## Account

* Login.
* Register.
* Logout.
* User Profile.
* Cloud sync cơ bản.

---

## Personal

* Create Task.
* Edit Task.
* Complete Task.
* Deadline.
* Priority.
* Estimated duration.
* Reminder.
* Repeat.

---

## Schedule

* Create Schedule.
* Timeline.
* Active Days.
* Enable / Disable.
* Skip Today.
* Run Today.
* Routine progress.

---

## Calendar

* Month view.
* Day view.
* Task timeline.
* Schedule timeline.

---

## Group

* Create Group.
* Invite Member.
* Leave Group.
* Assign Task.
* Group Task.
* Group Schedule.
* Group Reminder.

---

## Notification

* Scheduled Notification.
* Deadline Notification.
* Actionable Notification.
* Done.
* Snooze.
* Notification grouping.

---

## AI

V1 AI cần tập trung vào:

```text
Natural Language → Task

Natural Language → Schedule

Smart Reminder Suggestion

Morning Plan

Conflict Detection
```

Không cần cố nhồi quá nhiều AI feature ngay từ đầu.

---

# 33. Non-Goals của V1

Không làm:

* GPS.
* Map.
* Geofencing.
* Social Network.
* Video Call.
* Voice Call.
* Feed.
* Complex Chat.
* AI autonomous agent.
* AI tự ý thay đổi lịch.
* Desktop App.
* Web App.
* File management system lớn.
* Enterprise project management.

Điều quan trọng là:

> **Không biến Cue thành Tasker + Notion + Slack + Google Calendar + ChatGPT trong một app.**

---

# 34. UX Principle

## Simple outside, powerful inside

Người dùng không cần biết:

```text
Trigger
Condition
Rule Engine
Scheduler
```

Họ chỉ cần thấy:

```text
Remind me

Every Monday
at 20:00
```

Domain bên dưới có thể phức tạp.

UI phải đơn giản.

---

# 35. Progressive Disclosure

Không show tất cả option ngay.

Ví dụ khi tạo task:

```text
Title

When

Reminder

Schedule
```

Các option nâng cao nằm trong:

```text
More options
```

bao gồm:

```text
Repeat

Priority

Estimated Duration

Group

AI Suggestion
```

---

# 36. AI phải giải thích được

Không nên chỉ hiện:

> AI changed your schedule.

Nên hiện:

```text
Suggested change

Move Kotlin
19:00 → 20:30

Reason

You have a team meeting at 19:00.
```

User phải hiểu tại sao AI đề xuất.

---

# 37. AI không tự quyết định thay user

Một trong những nguyên tắc quan trọng nhất của Cue:

```text
AI Suggests
     ↓
User Reviews
     ↓
User Applies
```

Không phải:

```text
AI Decides
     ↓
Schedule Changed
```

Đặc biệt với:

* deadline;
* group task;
* reminder;
* schedule;
* delete;
* reschedule.

---

# 38. Offline First

Các chức năng quan trọng phải hoạt động khi không có Internet:

* xem task;
* xem schedule;
* complete task;
* notification;
* reminder;
* calendar;
* routine;
* widget.

AI và synchronization có thể cần Internet.

Nhưng core productivity app không được chết khi mất mạng.

---

# 39. Suggested Technical Direction

Android app:

```text
Kotlin

Jetpack Compose

Material 3

Coroutines / Flow

Room

DataStore

Hilt

WorkManager

AlarmManager

BroadcastReceiver

NotificationManager

Jetpack Glance
```

Backend có thể sử dụng:

```text
Supabase
```

hoặc một backend tương đương cho:

* Authentication.
* User Profile.
* Groups.
* Members.
* Group Tasks.
* Synchronization.

AI Provider nên được abstract để có thể thay đổi provider sau này.

Không hard-code toàn bộ application vào một AI vendor.

---

# 40. Architectural Direction

High-level architecture:

```text
UI
│
│ Jetpack Compose
↓
Presentation
│
│ ViewModel
│ StateFlow
↓
Domain
│
├── Task
├── Schedule
├── Reminder
├── Group
├── Planning
└── AI
│
↓
Data
│
├── Room
├── Remote API
└── Repository
```

System services:

```text
Reminder Engine

Scheduler

Notification Engine

Sync Engine

AI Engine

Widget
```

Các hệ thống này nên độc lập với nhau càng nhiều càng tốt.

---

# 41. Domain Model Direction

Mối quan hệ cơ bản:

```text
User
│
├── Personal Tasks
│
├── Schedules
│
└── Groups
       │
       ├── Members
       ├── Tasks
       └── Schedules
```

Task:

```text
Task
│
├── Reminder
├── Schedule
├── Assignee
└── AI Metadata
```

Không nên thiết kế database dựa trực tiếp trên UI.

Domain model phải phản ánh đúng business logic.

---

# 42. Team Development Principle

Khi một thành viên muốn thêm feature mới, hãy tự hỏi 4 câu:

### 1.

Feature này có giúp người dùng:

> **biết nên làm gì hoặc khi nào nên làm không?**

### 2.

Nó có thuộc:

```text
Task
Schedule
Reminder
Group
AI
```

không?

### 3.

Nếu bỏ feature này, core Cue có bị yếu đi không?

### 4.

Feature này có khiến app trở nên phức tạp hơn lợi ích mang lại không?

Nếu câu trả lời không rõ ràng:

> Không đưa vào V1.

---

# 43. Definition of Success

Cue thành công khi người dùng có thể thực hiện flow này:

```text
"Mình cần nộp Assignment thứ Sáu"
                ↓
             CUE
                ↓
         Task được tạo
                ↓
      Deadline được hiểu
                ↓
 AI tìm khoảng thời gian hợp lý
                ↓
  User thêm vào lịch hôm nay
                ↓
      Cue nhắc đúng thời điểm
                ↓
       User hoàn thành Task
```

Toàn bộ flow phải nhanh, rõ ràng và ít thao tác.

---

# 44. Product Identity

Cue không phải:

```text
Todo App
```

Cue không phải:

```text
Calendar App
```

Cue cũng không phải:

```text
AI Chatbot
```

Cue là sự kết hợp:

```text
Todo
    +
Schedule
    +
Reminder
    +
Group
    +
AI Planning
```

với mục tiêu:

> **giúp người dùng biến những việc họ muốn làm thành một kế hoạch thực tế có thể hoàn thành.**

---

# 45. One-Sentence Definition

Nếu một thành viên mới tham gia project và chỉ cần hiểu Cue trong một câu:

> **Cue là ứng dụng Android quản lý task và lịch trình cá nhân/nhóm, sử dụng AI để giúp người dùng lập kế hoạch, chọn thời điểm phù hợp và nhắc việc thông minh.**

---

# 46. North Star

Mọi quyết định UX, architecture và feature của Cue nên xoay quanh một câu hỏi:

> **“Feature này có giúp người dùng thực hiện đúng việc vào đúng thời điểm dễ hơn không?”**

Nếu có, nó phù hợp với Cue.

Nếu không, hãy cân nhắc loại bỏ.

---

# Project Status

```text
🟡 Product Design / Planning
```

Current direction:

```text
Account
+
Today
+
Task
+
Schedule
+
Calendar
+
Reminder
+
Groups
+
AI Planning
+
Notification
+
Widget
```

## Current Priority

```text
1. Finalize Product Requirements

2. Finalize Domain Model

3. Define V1 UX Flow

4. Design Architecture

5. Define Database Schema

6. Implement Core Features

7. Integrate AI

8. Group & Sync

9. Widget / Polish / Testing
```

> **Build the scheduling system first. Make AI amplify it later.**
