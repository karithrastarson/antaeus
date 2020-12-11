package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import mu.KotlinLogging

class BillingService(
    private val paymentProvider: PaymentProvider,
    private val customerService: CustomerService,
    private val invoiceService: InvoiceService
) {

    private val logger = KotlinLogging.logger {}

    /**
     * Fetch all invoices due and charge the customer
     */
    fun chargeDueInvoices(): Map<Invoice, String> {
        val results = mutableMapOf<Invoice, String>()
        val invoices = invoiceService.fetchInvoicesDue()
        invoices.forEach {
            val success = chargeInvoice(it)
            if (success) {
                results.put(it, "SUCCESS")
            } else {
                results.put(it, "FAILED")
            }
        }
        return results
    }

    fun chargeInvoice(invoice: Invoice): Boolean {
        val success = paymentProvider.charge(invoice)

        if (success) {
            logger.info("Invoice " + invoice.id + " for customer " + invoice.customerId + " successfully charged")
            updateInvoiceStatus(invoice, InvoiceStatus.PAID)
        } else {
            logger.info("Invoice " + invoice.id + " for customer " + invoice.customerId + " bounced")
            updateInvoiceStatus(invoice, InvoiceStatus.OVERDUE)
            customerService.incrementBounceCounter(invoice.customerId)
        }
        return success
    }

    fun updateInvoiceStatus(invoice: Invoice, status: InvoiceStatus) {
        invoiceService.updateInvoice(invoice, status)
    }
}
