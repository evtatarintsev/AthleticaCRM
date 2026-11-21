package org.athletica.crm.read.finance

/**
 * Денежное выражение SQL [expression] как JSON-объект `Money`. Все поддерживаемые валюты имеют
 * два разряда; выражения задаются только кодом проекций, не вводом пользователя.
 */
internal fun moneyJsonSql(expression: String) = "jsonb_build_object('minorUnits', (($expression) * 100)::bigint, 'currency', :currency)"

/** Условие «транзакция [alias] записана сегодня» в часовом поясе организации; сегодня — параметр `:today`. */
internal fun createdTodaySql(alias: String) = "($alias.created_at AT TIME ZONE (SELECT timezone FROM organizations WHERE id = :org))::date = :today"

/** Отбор касс выполняется в SQL, включая доступ владельца ко всем филиалам. */
internal const val CASH_ACCESS_SQL = """(c.branch_id IS NULL OR EXISTS (
    SELECT 1 FROM employees access_employee WHERE access_employee.id = :employee AND access_employee.org_id = :org AND
    (access_employee.is_owner OR access_employee.all_branches_access OR EXISTS (
        SELECT 1 FROM employee_branches eb WHERE eb.employee_id = access_employee.id AND eb.branch_id = c.branch_id
    ))
))"""

/** Все кассы исходной операции должны быть доступны для её отмены. */
internal const val TRANSACTION_CASH_ACCESS_SQL = """NOT EXISTS (
    SELECT 1 FROM ledger_postings cash_p JOIN ledger_accounts cash_a ON cash_a.id = cash_p.account_id AND cash_a.kind = 'cash' AND cash_a.org_id = :org
    JOIN cash_accounts c ON c.id = cash_a.cash_account_id AND c.org_id = :org
    WHERE cash_p.transaction_id = t.id AND NOT $CASH_ACCESS_SQL
)"""

/** Возврат закрывает возможность исправить одну оплату отдельно от продажи. */
internal const val SALE_HAS_RETURN_SQL = """EXISTS (
    SELECT 1 FROM ledger_transactions returned WHERE returned.sale_id = t.sale_id
    AND returned.org_id = t.org_id AND returned.kind = 'sale_return'
    AND NOT EXISTS (SELECT 1 FROM ledger_transactions r WHERE r.reverses_id = returned.id)
)"""
