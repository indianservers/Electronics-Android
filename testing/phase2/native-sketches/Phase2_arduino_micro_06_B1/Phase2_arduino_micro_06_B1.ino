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
int32_t excursions;
int32_t alarm;
int32_t motion0;
int32_t motion1;
double t0;
double t1;
double t2;
double t3;
double t4;
double t5;
double t6;
double t7;
// Intelligent Warehouse Monitoring
// Storage-zone thermal quorum and aisle motion
void setup(){Wire.begin();Serial1.begin(115200);pinMode(A1,OUTPUT);pinMode(D13,INPUT);pinMode(A0,INPUT);cycles=0;faults=0;excursions=0;alarm=0;motion0=0;motion1=0;t0=0;t1=0;t2=0;t3=0;t4=0;t5=0;t6=0;t7=0;delay(30000);}
void loop(){faults=0;excursions=0;Wire.beginTransmission(72);Wire.write(0);int32_t ack0=Wire.endTransmission(false);int32_t count0=Wire.requestFrom(72,2);int32_t hi0=Wire.read();int32_t lo0=Wire.read();if(ack0==0 && count0==2){int32_t raw0=hi0*256+lo0;if(raw0>=32768){raw0=raw0-65536;}t0=raw0/256.0;if(t0<5.0 || t0>35.0){excursions=excursions+1;}}else{faults=faults+1;}Wire.beginTransmission(73);Wire.write(0);int32_t ack1=Wire.endTransmission(false);int32_t count1=Wire.requestFrom(73,2);int32_t hi1=Wire.read();int32_t lo1=Wire.read();if(ack1==0 && count1==2){int32_t raw1=hi1*256+lo1;if(raw1>=32768){raw1=raw1-65536;}t1=raw1/256.0;if(t1<5.0 || t1>35.0){excursions=excursions+1;}}else{faults=faults+1;}Wire.beginTransmission(74);Wire.write(0);int32_t ack2=Wire.endTransmission(false);int32_t count2=Wire.requestFrom(74,2);int32_t hi2=Wire.read();int32_t lo2=Wire.read();if(ack2==0 && count2==2){int32_t raw2=hi2*256+lo2;if(raw2>=32768){raw2=raw2-65536;}t2=raw2/256.0;if(t2<5.0 || t2>35.0){excursions=excursions+1;}}else{faults=faults+1;}Wire.beginTransmission(75);Wire.write(0);int32_t ack3=Wire.endTransmission(false);int32_t count3=Wire.requestFrom(75,2);int32_t hi3=Wire.read();int32_t lo3=Wire.read();if(ack3==0 && count3==2){int32_t raw3=hi3*256+lo3;if(raw3>=32768){raw3=raw3-65536;}t3=raw3/256.0;if(t3<5.0 || t3>35.0){excursions=excursions+1;}}else{faults=faults+1;}Wire.beginTransmission(76);Wire.write(0);int32_t ack4=Wire.endTransmission(false);int32_t count4=Wire.requestFrom(76,2);int32_t hi4=Wire.read();int32_t lo4=Wire.read();if(ack4==0 && count4==2){int32_t raw4=hi4*256+lo4;if(raw4>=32768){raw4=raw4-65536;}t4=raw4/256.0;if(t4<5.0 || t4>35.0){excursions=excursions+1;}}else{faults=faults+1;}Wire.beginTransmission(77);Wire.write(0);int32_t ack5=Wire.endTransmission(false);int32_t count5=Wire.requestFrom(77,2);int32_t hi5=Wire.read();int32_t lo5=Wire.read();if(ack5==0 && count5==2){int32_t raw5=hi5*256+lo5;if(raw5>=32768){raw5=raw5-65536;}t5=raw5/256.0;if(t5<5.0 || t5>35.0){excursions=excursions+1;}}else{faults=faults+1;}Wire.beginTransmission(78);Wire.write(0);int32_t ack6=Wire.endTransmission(false);int32_t count6=Wire.requestFrom(78,2);int32_t hi6=Wire.read();int32_t lo6=Wire.read();if(ack6==0 && count6==2){int32_t raw6=hi6*256+lo6;if(raw6>=32768){raw6=raw6-65536;}t6=raw6/256.0;if(t6<5.0 || t6>35.0){excursions=excursions+1;}}else{faults=faults+1;}Wire.beginTransmission(79);Wire.write(0);int32_t ack7=Wire.endTransmission(false);int32_t count7=Wire.requestFrom(79,2);int32_t hi7=Wire.read();int32_t lo7=Wire.read();if(ack7==0 && count7==2){int32_t raw7=hi7*256+lo7;if(raw7>=32768){raw7=raw7-65536;}t7=raw7/256.0;if(t7<5.0 || t7>35.0){excursions=excursions+1;}}else{faults=faults+1;}motion0=1-digitalRead(D13);motion1=1-digitalRead(A0);alarm=0;if(faults>0 || excursions>=4 || motion0==1 || motion1==1){alarm=1;}if(alarm==1){digitalWrite(A1,HIGH);}else{digitalWrite(A1,LOW);}Serial1.println(faults);cycles=cycles+1;delay(1000);}
