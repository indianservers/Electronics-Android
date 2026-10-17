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
int32_t faults;
int32_t alarm;
double ax;
double t0;
double t1;
double t2;
double t3;
double t4;
double t5;
double t6;
double lux0;
int32_t lightInit0;
double lux1;
int32_t lightInit1;
// Industrial environmental monitoring: seven LM75B channels, two BH1750FVI and one ADXL345.
// Nominal monitored subsystem; see spec.json for absent application functions.
void setup(){Wire.begin();Serial1.begin(115200);pinMode(D13,OUTPUT);cycles=0;faults=0;alarm=0;ax=0;t0=0;t1=0;t2=0;t3=0;t4=0;t5=0;t6=0;lux0=0;lightInit0=0;lux1=0;lightInit1=0;Wire.beginTransmission(83);Wire.write(49);Wire.write(8);Wire.endTransmission();Wire.beginTransmission(83);Wire.write(45);Wire.write(8);Wire.endTransmission();Wire.beginTransmission(35);Wire.write(16);lightInit0=Wire.endTransmission();Wire.beginTransmission(92);Wire.write(16);lightInit1=Wire.endTransmission();delay(180);}
void loop(){faults=0;Wire.beginTransmission(72);Wire.write(0);int32_t a0=Wire.endTransmission(false);int32_t n0=Wire.requestFrom(72,2);int32_t h0=Wire.read();int32_t l0=Wire.read();int32_t r0=h0*256+l0;if(r0>=32768){r0=r0-65536;}if(a0==0 && n0==2){t0=r0/256.0;}else{faults=faults+1;}Wire.beginTransmission(73);Wire.write(0);int32_t a1=Wire.endTransmission(false);int32_t n1=Wire.requestFrom(73,2);int32_t h1=Wire.read();int32_t l1=Wire.read();int32_t r1=h1*256+l1;if(r1>=32768){r1=r1-65536;}if(a1==0 && n1==2){t1=r1/256.0;}else{faults=faults+1;}Wire.beginTransmission(74);Wire.write(0);int32_t a2=Wire.endTransmission(false);int32_t n2=Wire.requestFrom(74,2);int32_t h2=Wire.read();int32_t l2=Wire.read();int32_t r2=h2*256+l2;if(r2>=32768){r2=r2-65536;}if(a2==0 && n2==2){t2=r2/256.0;}else{faults=faults+1;}Wire.beginTransmission(75);Wire.write(0);int32_t a3=Wire.endTransmission(false);int32_t n3=Wire.requestFrom(75,2);int32_t h3=Wire.read();int32_t l3=Wire.read();int32_t r3=h3*256+l3;if(r3>=32768){r3=r3-65536;}if(a3==0 && n3==2){t3=r3/256.0;}else{faults=faults+1;}Wire.beginTransmission(76);Wire.write(0);int32_t a4=Wire.endTransmission(false);int32_t n4=Wire.requestFrom(76,2);int32_t h4=Wire.read();int32_t l4=Wire.read();int32_t r4=h4*256+l4;if(r4>=32768){r4=r4-65536;}if(a4==0 && n4==2){t4=r4/256.0;}else{faults=faults+1;}Wire.beginTransmission(77);Wire.write(0);int32_t a5=Wire.endTransmission(false);int32_t n5=Wire.requestFrom(77,2);int32_t h5=Wire.read();int32_t l5=Wire.read();int32_t r5=h5*256+l5;if(r5>=32768){r5=r5-65536;}if(a5==0 && n5==2){t5=r5/256.0;}else{faults=faults+1;}Wire.beginTransmission(78);Wire.write(0);int32_t a6=Wire.endTransmission(false);int32_t n6=Wire.requestFrom(78,2);int32_t h6=Wire.read();int32_t l6=Wire.read();int32_t r6=h6*256+l6;if(r6>=32768){r6=r6-65536;}if(a6==0 && n6==2){t6=r6/256.0;}else{faults=faults+1;}int32_t nl0=Wire.requestFrom(35,2);int32_t hl0=Wire.read();int32_t ll0=Wire.read();if(nl0==2 && lightInit0==0){lux0=(hl0*256+ll0)/1.2;}else{faults=faults+1;}int32_t nl1=Wire.requestFrom(92,2);int32_t hl1=Wire.read();int32_t ll1=Wire.read();if(nl1==2 && lightInit1==0){lux1=(hl1*256+ll1)/1.2;}else{faults=faults+1;}Wire.beginTransmission(83);Wire.write(50);int32_t aa=Wire.endTransmission(false);int32_t na=Wire.requestFrom(83,2);int32_t la=Wire.read();int32_t ha=Wire.read();int32_t ra=ha*256+la;if(ra>=32768){ra=ra-65536;}if(aa==0 && na==2){ax=ra*.0039;}else{faults=faults+1;}alarm=0;if(t0<0 || t0>60){alarm=1;}if(t1<0 || t1>60){alarm=1;}if(t2<0 || t2>60){alarm=1;}if(t3<0 || t3>60){alarm=1;}if(t4<0 || t4>60){alarm=1;}if(t5<0 || t5>60){alarm=1;}if(t6<0 || t6>60){alarm=1;}if(abs(ax)>.5 || lux0>20000 || lux1>20000){alarm=1;}if(faults>0){alarm=1;}digitalWrite(D13,alarm);Serial1.print(cycles);Serial1.print(":");Serial1.println(faults);cycles=cycles+1;delay(200);}