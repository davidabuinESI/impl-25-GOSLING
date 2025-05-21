package payment

//Clase abstracta base
abstract class PaymentMethod{
    
    def pay(amount: Money): TransactionStatus
    
    protected def logTransaction(amoun: Money, status: TransactionStatus): Unit = {
        println(s"[$name] Transacción de $amount€ => Estado: $status")
    }
}