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
double last;
double amps0;
double volts0;
double watts0;
double charge0;
double soc0;
double amps1;
double volts1;
double watts1;
double charge1;
double soc1;
double amps2;
double volts2;
double watts2;
double charge2;
double soc2;
double amps3;
double volts3;
double watts3;
double charge3;
double soc3;
double amps4;
double volts4;
double watts4;
double charge4;
double soc4;
double amps5;
double volts5;
double watts5;
double charge5;
double soc5;
double amps6;
double volts6;
double watts6;
double charge6;
double soc6;
double amps7;
double volts7;
double watts7;
double charge7;
double soc7;
double amps8;
double volts8;
double watts8;
double charge8;
double soc8;
double amps9;
double volts9;
double watts9;
double charge9;
double soc9;
// Five PV load channels and five battery discharge channels, 0.1 ohm external shunts.
// Charge bookkeeping assumes known initial 100 mAh and 200 mAh capacity; no voltage-derived SOC or battery chemistry.
void setup(){Wire.begin();Serial1.begin(115200);pinMode(A0,OUTPUT);cycles=0;faults=0;alarm=0;excursions=0;last=0;amps0=0;volts0=0;watts0=0;charge0=100;soc0=50;Wire.beginTransmission(64);Wire.write(5);Wire.write(16);Wire.write(0);Wire.endTransmission();amps1=0;volts1=0;watts1=0;charge1=100;soc1=50;Wire.beginTransmission(65);Wire.write(5);Wire.write(16);Wire.write(0);Wire.endTransmission();amps2=0;volts2=0;watts2=0;charge2=100;soc2=50;Wire.beginTransmission(66);Wire.write(5);Wire.write(16);Wire.write(0);Wire.endTransmission();amps3=0;volts3=0;watts3=0;charge3=100;soc3=50;Wire.beginTransmission(67);Wire.write(5);Wire.write(16);Wire.write(0);Wire.endTransmission();amps4=0;volts4=0;watts4=0;charge4=100;soc4=50;Wire.beginTransmission(68);Wire.write(5);Wire.write(16);Wire.write(0);Wire.endTransmission();amps5=0;volts5=0;watts5=0;charge5=100;soc5=50;Wire.beginTransmission(69);Wire.write(5);Wire.write(16);Wire.write(0);Wire.endTransmission();amps6=0;volts6=0;watts6=0;charge6=100;soc6=50;Wire.beginTransmission(70);Wire.write(5);Wire.write(16);Wire.write(0);Wire.endTransmission();amps7=0;volts7=0;watts7=0;charge7=100;soc7=50;Wire.beginTransmission(71);Wire.write(5);Wire.write(16);Wire.write(0);Wire.endTransmission();amps8=0;volts8=0;watts8=0;charge8=100;soc8=50;Wire.beginTransmission(72);Wire.write(5);Wire.write(16);Wire.write(0);Wire.endTransmission();amps9=0;volts9=0;watts9=0;charge9=100;soc9=50;Wire.beginTransmission(73);Wire.write(5);Wire.write(16);Wire.write(0);Wire.endTransmission();delay(2);last=micros();}
void loop(){faults=0;excursions=0;double now=micros();double dt=(now-last)/1000000.0;last=now;Wire.beginTransmission(64);Wire.write(2);int32_t ackbus0=Wire.endTransmission(false);int32_t nbus0=Wire.requestFrom(64,2);int32_t hibus0=Wire.read();int32_t lobus0=Wire.read();int32_t bus0=hibus0*256+lobus0;Wire.beginTransmission(64);Wire.write(4);int32_t ackcurrent0=Wire.endTransmission(false);int32_t ncurrent0=Wire.requestFrom(64,2);int32_t hicurrent0=Wire.read();int32_t locurrent0=Wire.read();int32_t current0=hicurrent0*256+locurrent0;Wire.beginTransmission(64);Wire.write(3);int32_t ackpower0=Wire.endTransmission(false);int32_t npower0=Wire.requestFrom(64,2);int32_t hipower0=Wire.read();int32_t lopower0=Wire.read();int32_t power0=hipower0*256+lopower0;Wire.beginTransmission(64);Wire.write(5);int32_t ackcal0=Wire.endTransmission(false);int32_t ncal0=Wire.requestFrom(64,2);int32_t hical0=Wire.read();int32_t local0=Wire.read();int32_t cal0=hical0*256+local0;if(ackbus0==0 && ackcurrent0==0 && ackpower0==0 && ackcal0==0 && ncal0==2 && cal0==4096 && nbus0==2 && ncurrent0==2 && npower0==2 && (bus0&1)==0){if(current0>=32768){current0=current0-65536;}amps0=current0*.0001;volts0=((bus0&65528)/8)*.004;watts0=power0*.002;charge0=charge0-amps0*dt/3.6;soc0=charge0/2;if(amps0>.3 || volts0<10 || volts0>23){excursions=excursions+1;}}else{faults=faults+1;}Wire.beginTransmission(65);Wire.write(2);int32_t ackbus1=Wire.endTransmission(false);int32_t nbus1=Wire.requestFrom(65,2);int32_t hibus1=Wire.read();int32_t lobus1=Wire.read();int32_t bus1=hibus1*256+lobus1;Wire.beginTransmission(65);Wire.write(4);int32_t ackcurrent1=Wire.endTransmission(false);int32_t ncurrent1=Wire.requestFrom(65,2);int32_t hicurrent1=Wire.read();int32_t locurrent1=Wire.read();int32_t current1=hicurrent1*256+locurrent1;Wire.beginTransmission(65);Wire.write(3);int32_t ackpower1=Wire.endTransmission(false);int32_t npower1=Wire.requestFrom(65,2);int32_t hipower1=Wire.read();int32_t lopower1=Wire.read();int32_t power1=hipower1*256+lopower1;Wire.beginTransmission(65);Wire.write(5);int32_t ackcal1=Wire.endTransmission(false);int32_t ncal1=Wire.requestFrom(65,2);int32_t hical1=Wire.read();int32_t local1=Wire.read();int32_t cal1=hical1*256+local1;if(ackbus1==0 && ackcurrent1==0 && ackpower1==0 && ackcal1==0 && ncal1==2 && cal1==4096 && nbus1==2 && ncurrent1==2 && npower1==2 && (bus1&1)==0){if(current1>=32768){current1=current1-65536;}amps1=current1*.0001;volts1=((bus1&65528)/8)*.004;watts1=power1*.002;charge1=charge1-amps1*dt/3.6;soc1=charge1/2;if(amps1>.3 || volts1<10 || volts1>23){excursions=excursions+1;}}else{faults=faults+1;}Wire.beginTransmission(66);Wire.write(2);int32_t ackbus2=Wire.endTransmission(false);int32_t nbus2=Wire.requestFrom(66,2);int32_t hibus2=Wire.read();int32_t lobus2=Wire.read();int32_t bus2=hibus2*256+lobus2;Wire.beginTransmission(66);Wire.write(4);int32_t ackcurrent2=Wire.endTransmission(false);int32_t ncurrent2=Wire.requestFrom(66,2);int32_t hicurrent2=Wire.read();int32_t locurrent2=Wire.read();int32_t current2=hicurrent2*256+locurrent2;Wire.beginTransmission(66);Wire.write(3);int32_t ackpower2=Wire.endTransmission(false);int32_t npower2=Wire.requestFrom(66,2);int32_t hipower2=Wire.read();int32_t lopower2=Wire.read();int32_t power2=hipower2*256+lopower2;Wire.beginTransmission(66);Wire.write(5);int32_t ackcal2=Wire.endTransmission(false);int32_t ncal2=Wire.requestFrom(66,2);int32_t hical2=Wire.read();int32_t local2=Wire.read();int32_t cal2=hical2*256+local2;if(ackbus2==0 && ackcurrent2==0 && ackpower2==0 && ackcal2==0 && ncal2==2 && cal2==4096 && nbus2==2 && ncurrent2==2 && npower2==2 && (bus2&1)==0){if(current2>=32768){current2=current2-65536;}amps2=current2*.0001;volts2=((bus2&65528)/8)*.004;watts2=power2*.002;charge2=charge2-amps2*dt/3.6;soc2=charge2/2;if(amps2>.3 || volts2<10 || volts2>23){excursions=excursions+1;}}else{faults=faults+1;}Wire.beginTransmission(67);Wire.write(2);int32_t ackbus3=Wire.endTransmission(false);int32_t nbus3=Wire.requestFrom(67,2);int32_t hibus3=Wire.read();int32_t lobus3=Wire.read();int32_t bus3=hibus3*256+lobus3;Wire.beginTransmission(67);Wire.write(4);int32_t ackcurrent3=Wire.endTransmission(false);int32_t ncurrent3=Wire.requestFrom(67,2);int32_t hicurrent3=Wire.read();int32_t locurrent3=Wire.read();int32_t current3=hicurrent3*256+locurrent3;Wire.beginTransmission(67);Wire.write(3);int32_t ackpower3=Wire.endTransmission(false);int32_t npower3=Wire.requestFrom(67,2);int32_t hipower3=Wire.read();int32_t lopower3=Wire.read();int32_t power3=hipower3*256+lopower3;Wire.beginTransmission(67);Wire.write(5);int32_t ackcal3=Wire.endTransmission(false);int32_t ncal3=Wire.requestFrom(67,2);int32_t hical3=Wire.read();int32_t local3=Wire.read();int32_t cal3=hical3*256+local3;if(ackbus3==0 && ackcurrent3==0 && ackpower3==0 && ackcal3==0 && ncal3==2 && cal3==4096 && nbus3==2 && ncurrent3==2 && npower3==2 && (bus3&1)==0){if(current3>=32768){current3=current3-65536;}amps3=current3*.0001;volts3=((bus3&65528)/8)*.004;watts3=power3*.002;charge3=charge3-amps3*dt/3.6;soc3=charge3/2;if(amps3>.3 || volts3<10 || volts3>23){excursions=excursions+1;}}else{faults=faults+1;}Wire.beginTransmission(68);Wire.write(2);int32_t ackbus4=Wire.endTransmission(false);int32_t nbus4=Wire.requestFrom(68,2);int32_t hibus4=Wire.read();int32_t lobus4=Wire.read();int32_t bus4=hibus4*256+lobus4;Wire.beginTransmission(68);Wire.write(4);int32_t ackcurrent4=Wire.endTransmission(false);int32_t ncurrent4=Wire.requestFrom(68,2);int32_t hicurrent4=Wire.read();int32_t locurrent4=Wire.read();int32_t current4=hicurrent4*256+locurrent4;Wire.beginTransmission(68);Wire.write(3);int32_t ackpower4=Wire.endTransmission(false);int32_t npower4=Wire.requestFrom(68,2);int32_t hipower4=Wire.read();int32_t lopower4=Wire.read();int32_t power4=hipower4*256+lopower4;Wire.beginTransmission(68);Wire.write(5);int32_t ackcal4=Wire.endTransmission(false);int32_t ncal4=Wire.requestFrom(68,2);int32_t hical4=Wire.read();int32_t local4=Wire.read();int32_t cal4=hical4*256+local4;if(ackbus4==0 && ackcurrent4==0 && ackpower4==0 && ackcal4==0 && ncal4==2 && cal4==4096 && nbus4==2 && ncurrent4==2 && npower4==2 && (bus4&1)==0){if(current4>=32768){current4=current4-65536;}amps4=current4*.0001;volts4=((bus4&65528)/8)*.004;watts4=power4*.002;charge4=charge4-amps4*dt/3.6;soc4=charge4/2;if(amps4>.3 || volts4<10 || volts4>23){excursions=excursions+1;}}else{faults=faults+1;}Wire.beginTransmission(69);Wire.write(2);int32_t ackbus5=Wire.endTransmission(false);int32_t nbus5=Wire.requestFrom(69,2);int32_t hibus5=Wire.read();int32_t lobus5=Wire.read();int32_t bus5=hibus5*256+lobus5;Wire.beginTransmission(69);Wire.write(4);int32_t ackcurrent5=Wire.endTransmission(false);int32_t ncurrent5=Wire.requestFrom(69,2);int32_t hicurrent5=Wire.read();int32_t locurrent5=Wire.read();int32_t current5=hicurrent5*256+locurrent5;Wire.beginTransmission(69);Wire.write(3);int32_t ackpower5=Wire.endTransmission(false);int32_t npower5=Wire.requestFrom(69,2);int32_t hipower5=Wire.read();int32_t lopower5=Wire.read();int32_t power5=hipower5*256+lopower5;Wire.beginTransmission(69);Wire.write(5);int32_t ackcal5=Wire.endTransmission(false);int32_t ncal5=Wire.requestFrom(69,2);int32_t hical5=Wire.read();int32_t local5=Wire.read();int32_t cal5=hical5*256+local5;if(ackbus5==0 && ackcurrent5==0 && ackpower5==0 && ackcal5==0 && ncal5==2 && cal5==4096 && nbus5==2 && ncurrent5==2 && npower5==2 && (bus5&1)==0){if(current5>=32768){current5=current5-65536;}amps5=current5*.0001;volts5=((bus5&65528)/8)*.004;watts5=power5*.002;charge5=charge5-amps5*dt/3.6;soc5=charge5/2;if(amps5>.3 || volts5<10 || volts5>23){excursions=excursions+1;}}else{faults=faults+1;}Wire.beginTransmission(70);Wire.write(2);int32_t ackbus6=Wire.endTransmission(false);int32_t nbus6=Wire.requestFrom(70,2);int32_t hibus6=Wire.read();int32_t lobus6=Wire.read();int32_t bus6=hibus6*256+lobus6;Wire.beginTransmission(70);Wire.write(4);int32_t ackcurrent6=Wire.endTransmission(false);int32_t ncurrent6=Wire.requestFrom(70,2);int32_t hicurrent6=Wire.read();int32_t locurrent6=Wire.read();int32_t current6=hicurrent6*256+locurrent6;Wire.beginTransmission(70);Wire.write(3);int32_t ackpower6=Wire.endTransmission(false);int32_t npower6=Wire.requestFrom(70,2);int32_t hipower6=Wire.read();int32_t lopower6=Wire.read();int32_t power6=hipower6*256+lopower6;Wire.beginTransmission(70);Wire.write(5);int32_t ackcal6=Wire.endTransmission(false);int32_t ncal6=Wire.requestFrom(70,2);int32_t hical6=Wire.read();int32_t local6=Wire.read();int32_t cal6=hical6*256+local6;if(ackbus6==0 && ackcurrent6==0 && ackpower6==0 && ackcal6==0 && ncal6==2 && cal6==4096 && nbus6==2 && ncurrent6==2 && npower6==2 && (bus6&1)==0){if(current6>=32768){current6=current6-65536;}amps6=current6*.0001;volts6=((bus6&65528)/8)*.004;watts6=power6*.002;charge6=charge6-amps6*dt/3.6;soc6=charge6/2;if(amps6>.3 || volts6<10 || volts6>23){excursions=excursions+1;}}else{faults=faults+1;}Wire.beginTransmission(71);Wire.write(2);int32_t ackbus7=Wire.endTransmission(false);int32_t nbus7=Wire.requestFrom(71,2);int32_t hibus7=Wire.read();int32_t lobus7=Wire.read();int32_t bus7=hibus7*256+lobus7;Wire.beginTransmission(71);Wire.write(4);int32_t ackcurrent7=Wire.endTransmission(false);int32_t ncurrent7=Wire.requestFrom(71,2);int32_t hicurrent7=Wire.read();int32_t locurrent7=Wire.read();int32_t current7=hicurrent7*256+locurrent7;Wire.beginTransmission(71);Wire.write(3);int32_t ackpower7=Wire.endTransmission(false);int32_t npower7=Wire.requestFrom(71,2);int32_t hipower7=Wire.read();int32_t lopower7=Wire.read();int32_t power7=hipower7*256+lopower7;Wire.beginTransmission(71);Wire.write(5);int32_t ackcal7=Wire.endTransmission(false);int32_t ncal7=Wire.requestFrom(71,2);int32_t hical7=Wire.read();int32_t local7=Wire.read();int32_t cal7=hical7*256+local7;if(ackbus7==0 && ackcurrent7==0 && ackpower7==0 && ackcal7==0 && ncal7==2 && cal7==4096 && nbus7==2 && ncurrent7==2 && npower7==2 && (bus7&1)==0){if(current7>=32768){current7=current7-65536;}amps7=current7*.0001;volts7=((bus7&65528)/8)*.004;watts7=power7*.002;charge7=charge7-amps7*dt/3.6;soc7=charge7/2;if(amps7>.3 || volts7<10 || volts7>23){excursions=excursions+1;}}else{faults=faults+1;}Wire.beginTransmission(72);Wire.write(2);int32_t ackbus8=Wire.endTransmission(false);int32_t nbus8=Wire.requestFrom(72,2);int32_t hibus8=Wire.read();int32_t lobus8=Wire.read();int32_t bus8=hibus8*256+lobus8;Wire.beginTransmission(72);Wire.write(4);int32_t ackcurrent8=Wire.endTransmission(false);int32_t ncurrent8=Wire.requestFrom(72,2);int32_t hicurrent8=Wire.read();int32_t locurrent8=Wire.read();int32_t current8=hicurrent8*256+locurrent8;Wire.beginTransmission(72);Wire.write(3);int32_t ackpower8=Wire.endTransmission(false);int32_t npower8=Wire.requestFrom(72,2);int32_t hipower8=Wire.read();int32_t lopower8=Wire.read();int32_t power8=hipower8*256+lopower8;Wire.beginTransmission(72);Wire.write(5);int32_t ackcal8=Wire.endTransmission(false);int32_t ncal8=Wire.requestFrom(72,2);int32_t hical8=Wire.read();int32_t local8=Wire.read();int32_t cal8=hical8*256+local8;if(ackbus8==0 && ackcurrent8==0 && ackpower8==0 && ackcal8==0 && ncal8==2 && cal8==4096 && nbus8==2 && ncurrent8==2 && npower8==2 && (bus8&1)==0){if(current8>=32768){current8=current8-65536;}amps8=current8*.0001;volts8=((bus8&65528)/8)*.004;watts8=power8*.002;charge8=charge8-amps8*dt/3.6;soc8=charge8/2;if(amps8>.3 || volts8<10 || volts8>23){excursions=excursions+1;}}else{faults=faults+1;}Wire.beginTransmission(73);Wire.write(2);int32_t ackbus9=Wire.endTransmission(false);int32_t nbus9=Wire.requestFrom(73,2);int32_t hibus9=Wire.read();int32_t lobus9=Wire.read();int32_t bus9=hibus9*256+lobus9;Wire.beginTransmission(73);Wire.write(4);int32_t ackcurrent9=Wire.endTransmission(false);int32_t ncurrent9=Wire.requestFrom(73,2);int32_t hicurrent9=Wire.read();int32_t locurrent9=Wire.read();int32_t current9=hicurrent9*256+locurrent9;Wire.beginTransmission(73);Wire.write(3);int32_t ackpower9=Wire.endTransmission(false);int32_t npower9=Wire.requestFrom(73,2);int32_t hipower9=Wire.read();int32_t lopower9=Wire.read();int32_t power9=hipower9*256+lopower9;Wire.beginTransmission(73);Wire.write(5);int32_t ackcal9=Wire.endTransmission(false);int32_t ncal9=Wire.requestFrom(73,2);int32_t hical9=Wire.read();int32_t local9=Wire.read();int32_t cal9=hical9*256+local9;if(ackbus9==0 && ackcurrent9==0 && ackpower9==0 && ackcal9==0 && ncal9==2 && cal9==4096 && nbus9==2 && ncurrent9==2 && npower9==2 && (bus9&1)==0){if(current9>=32768){current9=current9-65536;}amps9=current9*.0001;volts9=((bus9&65528)/8)*.004;watts9=power9*.002;charge9=charge9-amps9*dt/3.6;soc9=charge9/2;if(amps9>.3 || volts9<10 || volts9>23){excursions=excursions+1;}}else{faults=faults+1;}alarm=0;if(faults>0 || excursions>0){alarm=1;}digitalWrite(A0,alarm);Serial1.println(faults);cycles=cycles+1;delay(100);}