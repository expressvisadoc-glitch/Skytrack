# SkyTrack Android App — Content Specification for Redesign

**Scope:** what each screen of the SkyTrack employee app must contain: information, actions, states and copy.
Colour, type, layout and components are left to the design.

**Who the app is for:** employees, for their own attendance, breaks, leave, monthly hours, salary and payslips.
Administration happens on the website, not in the app. An administrator who opens the app sees their own
employee view.

**The app's one job:** make checking in at the office quick and certain, and make the money consequences of
attendance visible on the day they happen.

---

## 0. Conventions

| Term | Meaning |
| :--- | :--- |
| **Primary action** | The one thing the screen exists for. |
| **State** | A version of a screen: loading, empty, error, offline, success. |
| *Example:* | Sample data (fictional), see §9. |

### Formats

| Item | Format | Example |
| :--- | :--- | :--- |
| Date | EEE, d MMM | Thu, 12 Nov |
| Month | MMMM yyyy | November 2026 |
| Time | 12-hour, phone's local time | 9:52 AM |
| Live timer | hh:mm:ss | 03:14:07 |
| Duration | h + m | 8h 05m |
| Money | ₹, Indian grouping | ₹19,955 · ₹45 |
| Days | Half days allowed | 1.5 days |
| Distance | Metres | 12 m |

---

## 1. App map

```
Splash
 └─ Sign in ─ Forgot password
     └─ Main (bottom navigation, 4 tabs)
         ├─ Home          punch clock · location · salary card · bell → Notifications
         ├─ Leave         balances · apply · my requests
         ├─ Monthly       stats · calendar · daily log · timesheet PDF · payslips
         └─ Profile       details · settings · change password · sign out

Overlays: Notifications inbox · Payslip/Timesheet viewer · App update · Offline banner ·
          Fingerprint setup · Permission explanations
```

**Tab labels:** Home · Leave · Monthly · Profile. The current build uses "Leaves" and "Stats". Change them to
match the website.

---

## 2. Global elements

- **Offline banner**, on every screen while there is no connection: *"You're offline. Changes will sync when
  you reconnect."* While syncing: *"Syncing…"*. Note: check-in and check-out **cannot** work offline, because
  the server must verify location. Say that where it matters (§4.1).
- **Notification bell** on Home, with an unread count.
- **Pull to refresh** on Home, Leave and Monthly, with a "last updated" time.
- **Loading**: placeholder shapes that match the content.
- **Error**: plain words plus **Retry**.
- **Confirmation** for cancel-leave and sign-out.
- **App update** prompt: *"Update available"*, version, release notes (3 lines max), **Update** and **Later**.
  A required update has no Later.

---

## 3. Entry

### 3.1 Splash
- Logo, **SkyTrack**, *Skypass Visa Services*.
- Shown only while the session is checked. No marketing text needed.

### 3.2 Sign in
- *"Welcome back"* plus a one-line purpose: *"Sign in to record your attendance, breaks and leave."*
- **Employee ID** (for example SKY001). Hint: *"Your employee ID, not your email."*
- **Password**, with a show/hide control.
- **Remember me**.
- Primary: **Sign in**.
- **Sign in with fingerprint**, shown only when the person turned it on in Profile on this phone.
- **Forgot password?** opens §3.3.

Errors:
- *"Employee ID or password is incorrect."*
- *"Your account is suspended. Contact your administrator."*
- *"No connection. Connect to the internet to sign in."*
- Fingerprint changed on the phone: *"Your fingerprints changed. Sign in with your password to turn fingerprint
  sign-in back on."*

### 3.3 Forgot password
- *"Your administrator will set a temporary password for you."*
- Employee ID. Primary: **Send request**.
- Sent: *"Request sent. Your administrator will give you a temporary password."*

---

## 4. Home

**Purpose:** check in, take breaks, check out. Know whether it will work before you tap.

Order of content:
1. **Greeting**: *"Good morning, Ananya"*, today's date, and the bell with its unread count.
2. **Location status** (§4.1). Before check-in and before check-out only.
3. **Punch clock** (§4.2).
4. **Today**: check-in time, breaks (count and total), worked so far, check-out time.
5. **This month's salary** (§4.3).

### 4.1 Location status

The app reads the location only at that moment, never in the background. The server makes the final decision.
Every state needs a title, one line of explanation and, where possible, one action.

| State | Title | Explanation | Action |
| :--- | :--- | :--- | :--- |
| Checking | Checking your location… | Comparing your position with the office location. | (none) |
| Inside | You're at the office | 12 m from Skypass Office. You'll be checked again when you tap. | (none) |
| Outside | You're outside the office area | 140 m away. Check-in works within 30 m. | Retry |
| Low accuracy | Location accuracy is low | Move near a window or outdoors, turn on Wi-Fi and precise location. | Retry |
| Out of date | Location is out of date | Move to an open area, then retry. | Retry |
| Timed out | Couldn't get your location in time | Check that location is on, then retry. | Retry |
| Permission needed | Location permission needed | SkyTrack needs your location only when you check in or out. | Allow location |
| Permission denied permanently | Location permission needed | Turn it on in the app's settings. | App settings |
| Location off | Location services are off | Turn on location to check in or out. | Turn on location |
| Mock location | Mock location detected | Turn off any mock-location app to check in or out. | (none) |
| Offline | You're offline | Check-in and check-out need the internet so the server can verify your location. | Retry |
| Office not set up | Office location not set up | Contact your administrator. | (none) |
| Verification off | Check-in is paused | Your administrator has switched off attendance location checks. Contact them. | (none) |

Always add: *"Breaks are not affected."*. Breaks can start and end anywhere.

Optional: a small map with the office pin, the allowed radius and the person's position.

### 4.2 Punch clock

One main control whose label and meaning follow the state.

| State | Main control | Supporting content |
| :--- | :--- | :--- |
| Not checked in | **Check in** · "Ready" | Shift: *"General Shift · starts 10:00 AM"* |
| Checking in | (in progress) | *"Verifying your location…"* |
| Working | **Check out** | *"Working since 9:52 AM"*, live work timer, **Start break** |
| On break | (break timer) | *"On break · 12:31 elapsed"*, allowed break (*"60 min allowed"*), **End break**, and *"End your break before checking out."* |
| Checked out | (none) | *"Done for today"*, with in, out, breaks and worked |

After check-in, show the result in one line:
- On time: *"Checked in at 9:52 AM. You're on time."*
- Late, per-minute policy: *"Checked in 27 minutes late. The cost is shown in your salary breakdown."*
  Amounts stay behind the salary card's eye control, so nothing about pay is visible at a glance.
- Late, late-marks policy: *"Late mark 1 of 2 this month. The 2nd costs half a day's pay."*

Refused check-in: the reason from §4.1, in words. *"Check-in refused: you're 140 m from the office."*

Over the allowed break: *"Your break has passed 60 minutes."*

### 4.3 This month's salary (card)
- Title *"This month's salary"*, month, and the label **provisional** (final when the month is locked).
- Hidden by default: *"₹ • • • • •"* with **Show salary**. Hide again with **Hide salary**. Never shown
  automatically, never stored on the phone.
- When shown: **Expected net pay**, monthly gross, and *"Payable for days employed"* if they joined mid-month.
- **View breakdown**:
  - Absences (no check-in): days, ₹
  - Unpaid (LOP) leave: days, ₹
  - Late check-ins: count or minutes, ₹
  - **Days affecting pay**: a dated list, e.g. *"Thu, 12 Nov · late 27 min · −₹45"*.
- States: *"Calculating…"* · *"Salary not set yet. Please contact your administrator."* · *"You were not
  employed during this month."* · *"This month has no working days."* · *"Salary could not be calculated."*
  plus Retry.

---

## 5. Leave

### 5.1 Overview
- **Balances**: Casual and Sick (total · used · available). Loss of Pay: *"Unpaid. Always available."*
- Next and previous public holiday.
- Primary: **Apply for leave**.
- **My requests** (newest first): type, dates, days, half-day session, status (Pending / Approved / Rejected /
  Cancelled), once decided the paid/unpaid split, the manager's comment, and **Cancel request** while pending.
- Empty: *"No leave requests yet."*

### 5.2 Apply for leave (2 steps)

**Step 1 of 2: Type and dates**
- Leave type: Casual · Sick · Loss of pay. A type with zero balance is unavailable, with the reason:
  *"No casual days left."*
- Dates: from and to, from a month calendar that marks holidays and week-offs.
  - A holiday selected: *"12 Nov is a public holiday (Diwali). It won't be counted."*
  - A past date: *"You're applying for a past date. Continue?"*
- Half day: First half (morning) · Second half (afternoon).
- **Preview**: days requested, paid if approved, unpaid if approved.
  *Example:* "2 days · 1 paid · 1 unpaid (loss of pay)." Plus *"Sundays and holidays are not counted."*

**Step 2 of 2: Reason and submit**
- Reason (required), with quick picks: *Family event · Medical appointment · Personal matter*.
- Who decides: *"Goes to Rahul Menon, your reporting manager."* If none is assigned: *"Goes to your
  administrator."*
- Primary: **Submit request**.
- Sending: *"Sending your request…"*
- Success: *"Leave requested. You'll be notified when it's decided."*

### 5.3 Cancel a pending request
- Confirmation: *"Cancel this leave request?"* with dates and type.
- Actions: **Yes, cancel** and **Keep request**.

---

## 6. Monthly

### 6.1 Month summary
- Month selector (previous / next).
- Figures: days present, late days, absences, leaves (paid, unpaid), total active hours, daily average,
  attendance rate.
- Optional: hours vs target for the month.

### 6.2 Calendar
Each day shows one of: Worked · Late · Absent · Paid leave · Unpaid leave · Half day · Public holiday ·
Week off · Today · Future. Include a legend.

Selecting a day shows its detail: check-in, check-out, each break (start, end, length), worked, late minutes
and their cost, *"Edited by your administrator"* when that applies, and the leave or holiday name.

### 6.3 Daily log (list alternative to the calendar)
- Filters: All · Present · Late · Absent · Leave.
- Row: date, in, out, breaks, worked, status.
- Empty: *"No attendance records for this filter."*

### 6.4 Timesheet
- **Monthly timesheet (PDF)**: preview, **Share**, **Open in another app**.
- *"Generating…"* while it builds. If no viewer is available: *"No PDF app found. The file was saved."*

### 6.5 Payslips
- One row per locked month: month, net pay (hidden until **Show amounts**), version if revised (*"Revised ·
  v2"*), **View**.
- Note: *"Payslips are issued when payroll for a month is locked."*
- Empty: *"No payslips yet. They appear here after payroll for a month is locked."*
- Payslip viewer content: company letterhead, name, ID, designation, department, month, working days, paid
  days, gross, each deduction with days and ₹, net pay, version, issue date. Actions: **Share**, **Save**.

Prompt at the bottom: *"Something wrong? Tell your administrator before the month is locked."*

---

## 7. Notifications

### 7.1 Inbox (from the bell on Home)
- Newest first, grouped: Today · Yesterday · Earlier.
- Each item: title, one-line message, time (*"Just now"*, *"2 h ago"*, *"12 Nov"*), and an unread marker.
- Tapping an item opens the screen it is about (column "Opens").
- **Show older** at the end.
- Empty: *"No notifications yet. Shift reminders and updates about your attendance, leave and salary will
  appear here."*
- Error: *"Notifications unavailable."* plus **Try again**.

### 7.2 Notification messages (push and inbox)

| Code | When | Message (example) | Opens |
| :--- | :--- | :--- | :--- |
| A1 | 5 min before shift, not checked in | Your shift starts at 10:00 AM. Remember to check in. | Home |
| A2 | 1 min after shift start, not checked in | You haven't checked in today. | Home |
| A3 | Late check-in | You checked in 12 minutes late today. Late minutes this month: 39. | Home |
| A4 | One late mark before a deduction | One more late mark this month will cost half a day's pay. | Monthly |
| A5 | Break over the allowed length | Your break has passed 60 minutes. | Home |
| A6 | 10 min after shift end, still checked in | Your shift ended at 7:00 PM. Remember to check out. | Home |
| A7 | Next morning, no checkout yesterday | Your checkout on 27 Oct was missing. 6:00 PM was used. | Monthly |
| A8 | Next morning, absent without leave | You were marked absent on 27 Oct (1 day's pay). | Monthly |
| A9 | Admin changed your attendance | Your attendance for 27 Oct was updated by an administrator. | Monthly |
| L1 | Leave approved | Your leave for 18 Nov was approved. 1 day is unpaid. | Leave |
| L2 | Leave rejected | Your leave for 18 Nov was rejected: "{comment}" | Leave |
| L3 | Approved leave cancelled | Your approved leave for 18 Nov was cancelled. | Leave |
| L4 | Balance credited | Your leave balance was updated: +1 casual day. | Leave |
| L5 | Evening before leave | Reminder: your leave starts tomorrow. | Leave |
| H1 | Holiday published | New holiday: Diwali on 8 Nov. | Leave |
| H2 | Evening before a holiday | Tomorrow is a holiday (Diwali). | (none) |
| P1 | Estimated pay changed (once a day) | Your estimated salary has been updated. | Home (salary card) |
| P3 | Payslip issued | Your payslip for October 2026 is ready. | Monthly → Payslips |
| P4 | Payslip revised | Your payslip for October 2026 was revised. | Monthly → Payslips |
| P5 | Salary changed | Your salary details were updated. | Home (salary card) |
| S1 | Password changed by an admin | Your password was changed by an administrator. Sign in again. | Sign in |

Push notifications never show salary amounts. Amounts appear only inside the app, behind the eye control.

---

## 8. Profile

- Photo or initials, with **Change photo**: take a photo, choose from gallery, remove.
- Name, ID, designation, account status.
- **Employment details**: department, designation, work shift (name and times), reporting manager (or *"Not
  assigned"*), joining date, work email.
- **This month**: attendance rate, leave available. (Remove "YTD Overtime". See §10.)
- **Settings**:
  - **Notifications** on/off: *"Shift reminders, late marks, leave and payslip updates."* If the phone has
    blocked notifications: *"Allow notifications for SkyTrack in your phone settings."* plus **Open settings**.
  - **Fingerprint sign-in** on/off: *"Sign in without your password."* Setup asks for the password once:
    *"It is stored on this phone, locked by your fingerprint."* If there are no fingerprints:
    *"Add a fingerprint in your phone's settings first."*
- **Change password** (§8.1).
- **Help**: how to reach the administrator.
- **Sign out**, with confirmation. App version at the bottom.
- Note: *"To change your details, contact your administrator."*

### 8.1 Change password
- Current password, new password, confirm new password, each with show/hide.
- Live checklist: at least 8 characters · upper- and lower-case letters · at least one number · passwords
  match.
- Primary: **Update password**.
- Success: *"Password updated. You've been signed out on other devices."*
- *"Forgot your current password? Ask your administrator for a temporary one."*

### 8.2 Permission explanations (shown before the system prompt)
| Permission | When asked | Explanation |
| :--- | :--- | :--- |
| Location (while using the app) | First check-in | *"SkyTrack checks you're at the office when you check in or out. It never tracks you in the background."* |
| Notifications | After first sign-in | *"Get a reminder before your shift and know when leave is decided or a payslip is ready."* |
| Camera / Photos | Change photo | *"To take or choose your profile photo."* |

---

## 9. Example data for mock-ups (fictional)

- **Ananya Kumar** · SKY001 · Visa Consultant · Operations · General Shift 10:00 AM–7:00 PM.
- Reporting manager **Rahul Menon**. Office **Skypass Office**, radius 30 m. Today she is 12 m away.
- Balances: Casual 1 of 6 available, Sick 2 of 4.
- November 2026: 25 working days, gross ₹20,000, ₹800/day, ₹1.67/min. Late 27 min on Thu 12 Nov (−₹45).
  Expected net ₹19,955.
- Today: checked in 9:52 AM, break 1:05–1:37 PM, checked out 6:41 PM, worked 8h 17m.
- Payslips: October 2026 (v1), September 2026 (v2, revised).

Also test: a long name, a person with no salary, no manager, zero leave balance, every location error state,
and a 360 px-wide phone with large system font.

---

## 10. Copy to fix in the current app

| Current text | Where | Problem | Use instead |
| :--- | :--- | :--- | :--- |
| "YTD Overtime" | Profile | Overtime is not tracked | Remove, or "Leave available" |
| "Break (1h Max)" · "Tea / Lunch • 1h allowed" · "Break Duration (1h Max)" | Home | The allowed break is a setting | "Break · {allowed} min allowed" |
| "Approver notification sent via SkyTrack dispatch" | Leave | Jargon | "Rahul Menon has been notified." |
| "Request will be routed to Admin & HR Desk" | Leave | Vague | The approver's name, or "your administrator" |
| "Contact HR at support@skypass.ae" | Change password | Confirm this address is real | Your real support contact |
| "Protect Your Enterprise Account" · "Security Shield" · "ENCRYPTED SSL" · "Complexity Score" · "Passkeys match securely" | Change password | Marketing filler; "passkeys" means something else | "Change password", checklist, "Passwords match" |
| "SECURE ENTERPRISE MOBILITY" · "SYNCING WORKSPACE" | Splash | Filler | Remove |
| "Active Employment • Confirmed" · "Approved • Verified" · "Shift verification timeline" | Profile, Monthly | Unclear | "Active" · "Approved" · "Daily log" |
| "Offline Mode • Working from local cache" | Offline banner | Technical | "You're offline. Changes will sync when you reconnect." |
| "This feature is coming soon." (Notifications) | Dialog | Notifications now exist | Remove the dialog |
| Tab labels "Leaves", "Stats" | Bottom navigation | Differ from the website | "Leave", "Monthly" |
