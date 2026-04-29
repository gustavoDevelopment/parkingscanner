package com.parkingscanner.domain.model

data class Vigilante(
    val codigo: Int,
    val nombre: String
)

data class TipoVehiculo(
    val codigo: Int,
    val tipo: String
)

data class MedioPago(
    val codigo: Int,
    val tipo: String,
    val computa: Boolean = true
)

object CatalogoVigilantes {
    var vigilantes = mutableListOf(
        Vigilante(1, "Sara Daniela SanMiguel Vera"),
        Vigilante(2, "Bania Jumalay Jiames Becerra"),
        Vigilante(3, "Claudia Gonzales")
    )

    fun obtenerPorCodigo(codigo: Int): Vigilante? = vigilantes.find { it.codigo == codigo }
    fun buscarPorNombre(nombre: String): Vigilante? = vigilantes.find { it.nombre.equals(nombre, ignoreCase = true) }

    fun agregar(nombre: String): Vigilante {
        val nextCodigo = (vigilantes.maxOfOrNull { it.codigo } ?: 0) + 1
        val nuevo = Vigilante(nextCodigo, nombre)
        vigilantes.add(nuevo)
        return nuevo
    }
}

object CatalogoVehiculos {
    var vehiculos = mutableListOf(
        TipoVehiculo(1, "Automovil"),
        TipoVehiculo(2, "Moto"),
        TipoVehiculo(3, "Electrica")
    )

    fun obtenerPorCodigo(codigo: Int): TipoVehiculo? = vehiculos.find { it.codigo == codigo }
    fun buscarPorTipo(tipo: String): TipoVehiculo? = vehiculos.find { it.tipo.equals(tipo, ignoreCase = true) }

    fun agregar(tipo: String): TipoVehiculo {
        val nextCodigo = (vehiculos.maxOfOrNull { it.codigo } ?: 0) + 1
        val nuevo = TipoVehiculo(nextCodigo, tipo)
        vehiculos.add(nuevo)
        return nuevo
    }
}

object CatalogoMediosPago {
    var mediosPago = mutableListOf(
        MedioPago(1, "Efectivo", true),
        MedioPago(2, "QR", false),
        MedioPago(3, "ANULADO", false)
    )

    fun obtenerPorCodigo(codigo: Int): MedioPago? = mediosPago.find { it.codigo == codigo }
    fun buscarPorTipo(tipo: String): MedioPago? = mediosPago.find { it.tipo.equals(tipo, ignoreCase = true) }

    fun agregar(tipo: String, computa: Boolean): MedioPago {
        val nextCodigo = (mediosPago.maxOfOrNull { it.codigo } ?: 0) + 1
        val nuevo = MedioPago(nextCodigo, tipo, computa)
        mediosPago.add(nuevo)
        return nuevo
    }
}
