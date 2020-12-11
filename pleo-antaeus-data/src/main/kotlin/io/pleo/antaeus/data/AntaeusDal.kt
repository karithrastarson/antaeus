/*
    Implements the data access layer (DAL).
    The data access layer generates and executes requests to the database.

    See the `mappings` module for the conversions between database rows and Kotlin objects.
 */

package io.pleo.antaeus.data

import io.pleo.antaeus.data.InvoiceTable.dueDate
import io.pleo.antaeus.data.InvoiceTable.status
import io.pleo.antaeus.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime

class AntaeusDal(private val db: Database) {
    fun fetchInvoice(id: Int): Invoice? {
        // transaction(db) runs the internal query as a new database transaction.
        return transaction(db) {
            // Returns the first invoice with matching id.
            InvoiceTable
                .select { InvoiceTable.id.eq(id) }
                .firstOrNull()
                ?.toInvoice()
        }
    }

    fun fetchInvoices(): List<Invoice> {
        return transaction(db) {
            InvoiceTable
                .selectAll()
                .map { it.toInvoice() }
        }
    }

    fun fetchInvoicesDue(): List<Invoice> {
        return transaction(db) {
            InvoiceTable
                .select {
                    ((status.eq(InvoiceStatus.PENDING.name)) or (status.eq(InvoiceStatus.OVERDUE.name))) and dueDate.lessEq(
                        DateTime.now()
                    )
                }
                .map { it.toInvoice() }
        }
    }


    fun createInvoice(
        amount: Money,
        customer: Customer,
        status: InvoiceStatus = InvoiceStatus.PENDING,
        dueDate: DateTime
    ): Invoice? {
        val id = transaction(db) {
            // Insert the invoice and returns its new id.
            InvoiceTable
                .insert {
                    it[this.value] = amount.value
                    it[this.currency] = amount.currency.toString()
                    it[this.status] = status.toString()
                    it[this.customerId] = customer.id
                    it[this.dueDate] = dueDate
                } get InvoiceTable.id
        }

        return fetchInvoice(id)
    }

    fun fetchCustomer(id: Int): Customer? {
        return transaction(db) {
            CustomerTable
                .select { CustomerTable.id.eq(id) }
                .firstOrNull()
                ?.toCustomer()
        }
    }

    fun fetchCustomers(): List<Customer> {
        return transaction(db) {
            CustomerTable
                .selectAll()
                .map { it.toCustomer() }
        }
    }

    fun createCustomer(currency: Currency): Customer? {
        val id = transaction(db) {
            // Insert the customer and return its new id.
            CustomerTable.insert {
                it[this.currency] = currency.toString()
                it[this.invoicesBounced] = 0
            } get CustomerTable.id
        }

        return fetchCustomer(id)
    }

    fun updateInvoiceStatus(invoice: Invoice, invoiceStatus: InvoiceStatus): Invoice? {
        val id = transaction(db) {
            InvoiceTable.update({ InvoiceTable.id.eq(invoice.id) })
            { it[status] = invoiceStatus.name }
        }

        return fetchInvoice(id)
    }

    fun incrementCustomerBounce(customerId: Int): Customer? {
        val customer = fetchCustomer(customerId)
        if (customer == null) {
            return null
        }
        var counter = customer.invoicesBounced + 1

        val id = transaction(db) {
            CustomerTable.update({
                CustomerTable.id.eq(customerId)
            }) {
                it[invoicesBounced] = counter
            }
        }
        return fetchCustomer(id)
    }
}
