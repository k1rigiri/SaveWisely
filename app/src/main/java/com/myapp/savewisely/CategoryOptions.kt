package com.myapp.savewisely

object CategoryOptions {

    fun expenseCategory(): ArrayList<String> {
        val listExpense = ArrayList<String>()
        listExpense.add("Продукты питания")
        listExpense.add("Такси и ранспорт")
        listExpense.add("Развлечения")
        listExpense.add("Медицина")
        listExpense.add("Красота")
        listExpense.add("Спортзал")
        listExpense.add("Инвестиции")
        listExpense.add("Дом")
        listExpense.add("Ремонт")
        listExpense.add("Услуги")
        listExpense.add("Другое")

        return listExpense
    }

    fun incomeCategory(): ArrayList<String> {
        val listIncome = ArrayList<String>()
        listIncome.add("Зарплата")
        listIncome.add("Продажа")
        listIncome.add("Подарок")
        listIncome.add("Прибыль по инвестициям")
        listIncome.add("Государственные выплаты")
        listIncome.add("Пожертвования")
        listIncome.add("Другое")

        return listIncome
    }
}