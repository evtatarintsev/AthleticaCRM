/// Остаток занятий активного абонемента.
enum Remaining: Equatable, Sendable {
    /// Есть столько занятий, больше нуля.
    case left(Int)
    /// Занятия закончились.
    case exhausted
    /// Безлимит.
    case unlimited
}

/// Выводимые на дату свойства активного абонемента (порт `ActiveStatus` из `shared`); не хранятся.
struct ActiveStatus: Equatable, Sendable {
    /// Порог «заканчивается по дате»: до окончания столько дней или меньше.
    static let endingSoonDays = 7
    /// Порог «заканчивается по занятиям»: остаток ровно столько занятий.
    static let endingSoonSessions = 1

    /// Положение даты относительно срока.
    let termPosition: TermPosition
    /// Остаток занятий.
    let remaining: Remaining
    /// Причины признака «заканчивается»; пусто — не заканчивается.
    let endingSoon: Set<EndingSoonReason>

    /// «К закрытию»: срок вышел или занятия закончились.
    var closePending: Bool { termPosition == .EXPIRED || remaining == .exhausted }

    /// Свойства абонемента со сроком [startDate]..[endDate] и остатком [sessionsRemaining]
    /// (`nil` — безлимит) на дату [date].
    static func of(startDate: LocalDate, endDate: LocalDate, sessionsRemaining: Int?, date: LocalDate) -> ActiveStatus {
        let termPosition: TermPosition = date < startDate ? .NOT_STARTED : (date > endDate ? .EXPIRED : .IN_TERM)
        let remaining: Remaining = switch sessionsRemaining {
        case nil: .unlimited
        case let .some(left) where left <= 0: .exhausted
        case let .some(left): .left(left)
        }
        var endingSoon: Set<EndingSoonReason> = []
        if termPosition == .IN_TERM {
            if date.daysUntil(endDate) <= endingSoonDays {
                endingSoon.insert(.BY_DATE)
            }
            if remaining == .left(endingSoonSessions) {
                endingSoon.insert(.BY_SESSIONS)
            }
        }
        return ActiveStatus(termPosition: termPosition, remaining: remaining, endingSoon: endingSoon)
    }
}
