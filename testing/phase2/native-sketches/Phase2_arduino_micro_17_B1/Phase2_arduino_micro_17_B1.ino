// Separate native AVR adaptation: persistent state, int32_t arithmetic, header aliases and SPISettings API.
#include <Wire.h>
#include <SPI.h>
#include <math.h>
#include <stdint.h>
static_assert(sizeof(int)==2, "Expected AVR int ABI");
static_assert(sizeof(int32_t)==4, "Register arithmetic must use 32-bit state");
#ifndef D0
#define D0 0
#endif
#ifndef D1
#define D1 1
#endif
#ifndef D2
#define D2 2
#endif
#ifndef D3
#define D3 3
#endif
#ifndef D4
#define D4 4
#endif
#ifndef D5
#define D5 5
#endif
#ifndef D6
#define D6 6
#endif
#ifndef D7
#define D7 7
#endif
#ifndef D8
#define D8 8
#endif
#ifndef D9
#define D9 9
#endif
#ifndef D10
#define D10 10
#endif
#ifndef D11
#define D11 11
#endif
#ifndef D12
#define D12 12
#endif
#ifndef D13
#define D13 13
#endif
#ifndef D14
#define D14 14
#endif
#ifndef D15
#define D15 15
#endif
#ifndef D16
#define D16 16
#endif
#ifndef D17
#define D17 17
#endif
#ifndef D18
#define D18 18
#endif
#ifndef D19
#define D19 19
#endif
#ifndef D20
#define D20 20
#endif
#ifndef D21
#define D21 21
#endif
#ifndef D22
#define D22 22
#endif
#ifndef D23
#define D23 23
#endif
#ifndef D24
#define D24 24
#endif
#ifndef D25
#define D25 25
#endif
#ifndef D26
#define D26 26
#endif
#ifndef D27
#define D27 27
#endif
#ifndef D28
#define D28 28
#endif
#ifndef D29
#define D29 29
#endif
#ifndef D30
#define D30 30
#endif
#ifndef D31
#define D31 31
#endif
#ifndef D32
#define D32 32
#endif
#ifndef D33
#define D33 33
#endif
#ifndef D34
#define D34 34
#endif
#ifndef D35
#define D35 35
#endif
#ifndef D36
#define D36 36
#endif
#ifndef D37
#define D37 37
#endif
#ifndef D38
#define D38 38
#endif
#ifndef D39
#define D39 39
#endif
#ifndef D40
#define D40 40
#endif
#ifndef D41
#define D41 41
#endif
#ifndef D42
#define D42 42
#endif
#ifndef D43
#define D43 43
#endif
#ifndef D44
#define D44 44
#endif
#ifndef D45
#define D45 45
#endif
#ifndef D46
#define D46 46
#endif
#ifndef D47
#define D47 47
#endif
#ifndef D48
#define D48 48
#endif
#ifndef D49
#define D49 49
#endif
#ifndef D50
#define D50 50
#endif
#ifndef D51
#define D51 51
#endif
#ifndef D52
#define D52 52
#endif
#ifndef D53
#define D53 53
#endif
#ifndef D54
#define D54 54
#endif
#ifndef D55
#define D55 55
#endif
#ifndef D56
#define D56 56
#endif
#ifndef D57
#define D57 57
#endif
#ifndef D58
#define D58 58
#endif
#ifndef D59
#define D59 59
#endif
#ifndef D60
#define D60 60
#endif
#ifndef D61
#define D61 61
#endif
#ifndef D62
#define D62 62
#endif
#ifndef D63
#define D63 63
#endif
#ifndef D64
#define D64 64
#endif
#ifndef D65
#define D65 65
#endif
#ifndef D66
#define D66 66
#endif
#ifndef D67
#define D67 67
#endif
#ifndef D68
#define D68 68
#endif
#ifndef D69
#define D69 69
#endif
int32_t cycles;
int32_t received;
int32_t faults;
int32_t alarm;
int32_t remoteFaults;
int32_t remoteExcursions;
double lastSeen;
// Dashboard reads the remote ECU frame, and fails safe after 200 ms without a frame.
void setup(){pinMode(D13,OUTPUT);digitalWrite(D13,HIGH);SPI.begin();SPI.setClockDivider(32);digitalWrite(D13,LOW);SPI.transfer(192);digitalWrite(D13,HIGH);digitalWrite(D13,LOW);SPI.transfer(2);SPI.transfer(40);SPI.transfer(5);digitalWrite(D13,HIGH);digitalWrite(D13,LOW);SPI.transfer(2);SPI.transfer(41);SPI.transfer(177);digitalWrite(D13,HIGH);digitalWrite(D13,LOW);SPI.transfer(2);SPI.transfer(42);SPI.transfer(0);digitalWrite(D13,HIGH);digitalWrite(D13,LOW);SPI.transfer(2);SPI.transfer(96);SPI.transfer(96);digitalWrite(D13,HIGH);digitalWrite(D13,LOW);SPI.transfer(2);SPI.transfer(112);SPI.transfer(96);digitalWrite(D13,HIGH);digitalWrite(D13,LOW);SPI.transfer(2);SPI.transfer(15);SPI.transfer(8);digitalWrite(D13,HIGH);Serial1.begin(115200);pinMode(A0,OUTPUT);cycles=0;received=0;faults=0;alarm=0;remoteFaults=0;remoteExcursions=0;lastSeen=0;}void loop(){digitalWrite(D13,LOW);SPI.transfer(3);SPI.transfer(44);int32_t flags=SPI.transfer(0);digitalWrite(D13,HIGH);if((flags&1)!=0){digitalWrite(D13,LOW);SPI.transfer(3);SPI.transfer(97);int32_t idHi=SPI.transfer(0);int32_t idLo=SPI.transfer(0);SPI.transfer(0);SPI.transfer(0);int32_t dlc=SPI.transfer(0);int32_t f=SPI.transfer(0);int32_t e=SPI.transfer(0);int32_t co=SPI.transfer(0);int32_t pressure=SPI.transfer(0);digitalWrite(D13,HIGH);if(idHi*8+idLo/32==801 && dlc==4){remoteFaults=f;remoteExcursions=e;lastSeen=millis();received=received+1;}digitalWrite(D13,LOW);SPI.transfer(5);SPI.transfer(44);SPI.transfer(1);SPI.transfer(0);digitalWrite(D13,HIGH);}faults=remoteFaults;if(millis()-lastSeen>200){faults=faults+1;}alarm=0;if(faults>0 || remoteExcursions>0){alarm=1;}digitalWrite(A0,alarm);Serial1.println(faults);cycles=cycles+1;delay(10);}