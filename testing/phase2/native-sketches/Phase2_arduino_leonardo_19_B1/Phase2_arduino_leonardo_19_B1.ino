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
int32_t excursions;
double roll;
double velocity;
double position;
double energy;
double rms;
double previousRms;
double trend;
double timeToThreshold;
double last;
double co;
double depth;
int32_t co2;
double t0;
double t1;
double t2;
double t3;
double t4;
double t5;
double t6;
double t7;
// Seismic and hydrostatic flood alert
// Raw sensor protocols. Illustrative application policy; no native CPU or vendor library execution.
void setup(){Wire.begin();Serial1.begin(115200);pinMode(A1,OUTPUT);cycles=0;faults=0;alarm=0;excursions=0;roll=0;velocity=0;position=0;energy=0;rms=0;previousRms=0;trend=0;timeToThreshold=0;last=0;co=0;depth=0;co2=0;t0=0;t1=0;t2=0;t3=0;t4=0;t5=0;t6=0;t7=0;Wire.beginTransmission(83);Wire.write(49);Wire.write(8);Wire.endTransmission();Wire.beginTransmission(83);Wire.write(45);Wire.write(8);Wire.endTransmission();Wire.beginTransmission(106);Wire.write(16);Wire.write(64);Wire.write(64);Wire.endTransmission();last=micros();}
void loop(){faults=0;excursions=0;Wire.beginTransmission(72);Wire.write(0);int32_t ack0=Wire.endTransmission(false);int32_t n0=Wire.requestFrom(72,2);int32_t hi0=Wire.read();int32_t lo0=Wire.read();if(ack0==0 && n0==2){int32_t raw0=hi0*256+lo0;if(raw0>=32768){raw0=raw0-65536;}t0=raw0/256.0;if(t0<0 || t0>70){excursions=excursions+1;}}else{faults=faults+1;}Wire.beginTransmission(73);Wire.write(0);int32_t ack1=Wire.endTransmission(false);int32_t n1=Wire.requestFrom(73,2);int32_t hi1=Wire.read();int32_t lo1=Wire.read();if(ack1==0 && n1==2){int32_t raw1=hi1*256+lo1;if(raw1>=32768){raw1=raw1-65536;}t1=raw1/256.0;if(t1<0 || t1>70){excursions=excursions+1;}}else{faults=faults+1;}Wire.beginTransmission(74);Wire.write(0);int32_t ack2=Wire.endTransmission(false);int32_t n2=Wire.requestFrom(74,2);int32_t hi2=Wire.read();int32_t lo2=Wire.read();if(ack2==0 && n2==2){int32_t raw2=hi2*256+lo2;if(raw2>=32768){raw2=raw2-65536;}t2=raw2/256.0;if(t2<0 || t2>70){excursions=excursions+1;}}else{faults=faults+1;}Wire.beginTransmission(75);Wire.write(0);int32_t ack3=Wire.endTransmission(false);int32_t n3=Wire.requestFrom(75,2);int32_t hi3=Wire.read();int32_t lo3=Wire.read();if(ack3==0 && n3==2){int32_t raw3=hi3*256+lo3;if(raw3>=32768){raw3=raw3-65536;}t3=raw3/256.0;if(t3<0 || t3>70){excursions=excursions+1;}}else{faults=faults+1;}Wire.beginTransmission(76);Wire.write(0);int32_t ack4=Wire.endTransmission(false);int32_t n4=Wire.requestFrom(76,2);int32_t hi4=Wire.read();int32_t lo4=Wire.read();if(ack4==0 && n4==2){int32_t raw4=hi4*256+lo4;if(raw4>=32768){raw4=raw4-65536;}t4=raw4/256.0;if(t4<0 || t4>70){excursions=excursions+1;}}else{faults=faults+1;}Wire.beginTransmission(77);Wire.write(0);int32_t ack5=Wire.endTransmission(false);int32_t n5=Wire.requestFrom(77,2);int32_t hi5=Wire.read();int32_t lo5=Wire.read();if(ack5==0 && n5==2){int32_t raw5=hi5*256+lo5;if(raw5>=32768){raw5=raw5-65536;}t5=raw5/256.0;if(t5<0 || t5>70){excursions=excursions+1;}}else{faults=faults+1;}Wire.beginTransmission(78);Wire.write(0);int32_t ack6=Wire.endTransmission(false);int32_t n6=Wire.requestFrom(78,2);int32_t hi6=Wire.read();int32_t lo6=Wire.read();if(ack6==0 && n6==2){int32_t raw6=hi6*256+lo6;if(raw6>=32768){raw6=raw6-65536;}t6=raw6/256.0;if(t6<0 || t6>70){excursions=excursions+1;}}else{faults=faults+1;}Wire.beginTransmission(79);Wire.write(0);int32_t ack7=Wire.endTransmission(false);int32_t n7=Wire.requestFrom(79,2);int32_t hi7=Wire.read();int32_t lo7=Wire.read();if(ack7==0 && n7==2){int32_t raw7=hi7*256+lo7;if(raw7>=32768){raw7=raw7-65536;}t7=raw7/256.0;if(t7<0 || t7>70){excursions=excursions+1;}}else{faults=faults+1;}Wire.beginTransmission(83);Wire.write(50);int32_t acka=Wire.endTransmission(false);int32_t na=Wire.requestFrom(83,6);int32_t loax=Wire.read();int32_t hiax=Wire.read();int32_t rawax=hiax*256+loax;if(rawax>=32768){rawax=rawax-65536;}double ax=rawax*0.0039;int32_t loay=Wire.read();int32_t hiay=Wire.read();int32_t raway=hiay*256+loay;if(raway>=32768){raway=raway-65536;}double ay=raway*0.0039;int32_t loaz=Wire.read();int32_t hiaz=Wire.read();int32_t rawaz=hiaz*256+loaz;if(rawaz>=32768){rawaz=rawaz-65536;}double az=rawaz*0.0039;if(acka!=0 || na!=6){faults=faults+1;}Wire.beginTransmission(106);Wire.write(34);int32_t ackg=Wire.endTransmission(false);int32_t ng=Wire.requestFrom(106,6);int32_t logx=Wire.read();int32_t higx=Wire.read();int32_t rawgx=higx*256+logx;if(rawgx>=32768){rawgx=rawgx-65536;}double gx=rawgx*0.00875;int32_t logy=Wire.read();int32_t higy=Wire.read();int32_t rawgy=higy*256+logy;if(rawgy>=32768){rawgy=rawgy-65536;}double gy=rawgy*0.00875;int32_t logz=Wire.read();int32_t higz=Wire.read();int32_t rawgz=higz*256+logz;if(rawgz>=32768){rawgz=rawgz-65536;}double gz=rawgz*0.00875;Wire.beginTransmission(106);Wire.write(40);int32_t acki=Wire.endTransmission(false);int32_t ni=Wire.requestFrom(106,6);int32_t loix=Wire.read();int32_t hiix=Wire.read();int32_t rawix=hiix*256+loix;if(rawix>=32768){rawix=rawix-65536;}double ix=rawix*6.1E-5;int32_t loiy=Wire.read();int32_t hiiy=Wire.read();int32_t rawiy=hiiy*256+loiy;if(rawiy>=32768){rawiy=rawiy-65536;}double iy=rawiy*6.1E-5;int32_t loiz=Wire.read();int32_t hiiz=Wire.read();int32_t rawiz=hiiz*256+loiz;if(rawiz>=32768){rawiz=rawiz-65536;}double iz=rawiz*6.1E-5;if(ackg!=0 || ng!=6 || acki!=0 || ni!=6){faults=faults+1;}double now=micros();double dt=(now-last)/1000000.0;last=now;if(acka==0 && na==6 && ackg==0 && ng==6 && acki==0 && ni==6){depth=((analogRead(A0)+.5)*5.0*2.0/1024/5.1-.04)/.09/9.80665;if(abs(ax)>.3 || abs(ix)>.3 || depth>.5 || depth< -.02){excursions=excursions+1;}}alarm=0;if(faults>0 || excursions>0){alarm=1;}digitalWrite(A1,alarm);Serial1.println(faults);cycles=cycles+1;delay(100);}