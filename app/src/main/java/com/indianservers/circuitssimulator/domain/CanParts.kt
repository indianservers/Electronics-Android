package com.indianservers.circuitssimulator.domain
object CanParts {
    val pins=listOf("VCC","GND","MOSI","MISO","SCK","CS","CANH","CANL","VTRANS")
    val pinNames=mapOf(Kind.MCP2515_CAN to pins)
    val limitations=listOf("Nine-pin simulated carrier: MCP2515 with a 16 MHz oscillator and MCP2562FD transceiver. Controller VDD and transceiver VIO use VCC (2.7–5.5 V); separate VTRANS must be 4.5–5.5 V. No commercial breakout pinout is implied.",
        "SPI reset, register read/write/bit-modify and RTS for TX buffer 0; two receive buffers, polling flags, standard 11-bit data frames, accept-all RX mode, normal one-shot and loopback modes. Other modes, extended/RTR frames, filters, TX1/TX2, interrupts and automatic retries are unsupported.",
        "Transaction-level CAN delivery requires connected CANH/CANL, common ground, compatible CNF bit rate and 50–65 ohm parallel termination. Nominal frame scheduling uses 47+8×DLC bits; arbitration, stuffing, CRC errors, propagation, transients and fault-confinement counters are not simulated.",
        "SPI electrical edges use the runtime 1 µs resolution (500 kHz resolved clock); the vehicle fixture explicitly requests 500 kHz. Faster hardware SPI capability is metadata only.",
        "DC transceiver bias and supply loads are educational equivalents. Differential voltage waveforms and automotive physical-layer compliance are not claimed.")
    val metadata=DatasheetMetadata(manufacturer="Microchip Technology",partNumber="MCP2515 + MCP2562FD",packageName="Nine-pin simulated SPI/CAN carrier",
        physicalPinOrder=pins,referenceUrl="https://ww1.microchip.com/downloads/aemDocuments/documents/APID/ProductDocuments/DataSheets/MCP2515-Family-Data-Sheet-DS20001801K.pdf",
        revision="MCP2515 DS20001801K (2021); transceiver DS20005284A (2014)",lastVerifiedUtc="2026-10-16",
        sourceNotes="MCP2515 sections 3/4/5/10/12; transceiver https://ww1.microchip.com/downloads/aemDocuments/documents/OTH/ProductDocuments/DataSheets/20005284A.pdf . Carrier header is separate from both IC package pin orders.")
    val definitions=listOf(Definition(Kind.MCP2515_CAN,"MCP2515 + MCP2562FD transaction-level CAN carrier",emptyList(),datasheet=metadata,
        keywords=listOf("CAN","SPI","vehicle","MCP2515"),applications=listOf("Two-controller vehicle telemetry and link fault detection"),limitations=limitations))
}
