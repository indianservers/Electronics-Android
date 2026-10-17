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
double reading0;
double reading1;
double reading2;
double reading3;
double reading4;
double reading5;
double reading6;
double reading7;
double reading8;
double reading9;
// EZO process monitoring; ASCII protocol, ten independent calibrated probes.
void setup(){Wire.begin();Serial1.begin(115200);pinMode(A0,OUTPUT);cycles=0;faults=0;excursions=0;alarm=0;reading0=0;reading1=0;reading2=0;reading3=0;reading4=0;reading5=0;reading6=0;reading7=0;reading8=0;reading9=0;}
void loop(){faults=0;excursions=0;Wire.beginTransmission(32);Wire.write(82);int32_t ack0=Wire.endTransmission();Wire.beginTransmission(33);Wire.write(82);int32_t ack1=Wire.endTransmission();Wire.beginTransmission(34);Wire.write(82);int32_t ack2=Wire.endTransmission();Wire.beginTransmission(35);Wire.write(82);int32_t ack3=Wire.endTransmission();Wire.beginTransmission(36);Wire.write(82);int32_t ack4=Wire.endTransmission();Wire.beginTransmission(37);Wire.write(82);int32_t ack5=Wire.endTransmission();Wire.beginTransmission(38);Wire.write(82);int32_t ack6=Wire.endTransmission();Wire.beginTransmission(39);Wire.write(82);int32_t ack7=Wire.endTransmission();Wire.beginTransmission(40);Wire.write(82);int32_t ack8=Wire.endTransmission();Wire.beginTransmission(41);Wire.write(82);int32_t ack9=Wire.endTransmission();delay(900);int32_t count0=Wire.requestFrom(32,16);int32_t status0=Wire.read();double value0=0;double scale0=1;int32_t dot0=0;int32_t digits0=0;int32_t bad0=0;int32_t end0=0;for(int32_t j0=0;j0<15;j0=j0+1){int32_t ch0=Wire.read();if(end0==0){if(ch0==0){end0=1;}else{if(ch0==46 && dot0==0){dot0=1;}else{if(ch0>=48 && ch0<=57){value0=value0*10+ch0-48;digits0=digits0+1;if(dot0==1){scale0=scale0*10;}}else{bad0=1;}}}}}if(ack0==0 && count0==16 && status0==1 && digits0>0 && bad0==0 && end0==1){reading0=value0/scale0;if(reading0<6.5 || reading0>8.5){excursions=excursions+1;}}else{faults=faults+1;}int32_t count1=Wire.requestFrom(33,16);int32_t status1=Wire.read();double value1=0;double scale1=1;int32_t dot1=0;int32_t digits1=0;int32_t bad1=0;int32_t end1=0;for(int32_t j1=0;j1<15;j1=j1+1){int32_t ch1=Wire.read();if(end1==0){if(ch1==0){end1=1;}else{if(ch1==46 && dot1==0){dot1=1;}else{if(ch1>=48 && ch1<=57){value1=value1*10+ch1-48;digits1=digits1+1;if(dot1==1){scale1=scale1*10;}}else{bad1=1;}}}}}if(ack1==0 && count1==16 && status1==1 && digits1>0 && bad1==0 && end1==1){reading1=value1/scale1;if(reading1<6.5 || reading1>8.5){excursions=excursions+1;}}else{faults=faults+1;}int32_t count2=Wire.requestFrom(34,16);int32_t status2=Wire.read();double value2=0;double scale2=1;int32_t dot2=0;int32_t digits2=0;int32_t bad2=0;int32_t end2=0;for(int32_t j2=0;j2<15;j2=j2+1){int32_t ch2=Wire.read();if(end2==0){if(ch2==0){end2=1;}else{if(ch2==46 && dot2==0){dot2=1;}else{if(ch2>=48 && ch2<=57){value2=value2*10+ch2-48;digits2=digits2+1;if(dot2==1){scale2=scale2*10;}}else{bad2=1;}}}}}if(ack2==0 && count2==16 && status2==1 && digits2>0 && bad2==0 && end2==1){reading2=value2/scale2;if(reading2<6.5 || reading2>8.5){excursions=excursions+1;}}else{faults=faults+1;}int32_t count3=Wire.requestFrom(35,16);int32_t status3=Wire.read();double value3=0;double scale3=1;int32_t dot3=0;int32_t digits3=0;int32_t bad3=0;int32_t end3=0;for(int32_t j3=0;j3<15;j3=j3+1){int32_t ch3=Wire.read();if(end3==0){if(ch3==0){end3=1;}else{if(ch3==46 && dot3==0){dot3=1;}else{if(ch3>=48 && ch3<=57){value3=value3*10+ch3-48;digits3=digits3+1;if(dot3==1){scale3=scale3*10;}}else{bad3=1;}}}}}if(ack3==0 && count3==16 && status3==1 && digits3>0 && bad3==0 && end3==1){reading3=value3/scale3;if(reading3<6.5 || reading3>8.5){excursions=excursions+1;}}else{faults=faults+1;}int32_t count4=Wire.requestFrom(36,16);int32_t status4=Wire.read();double value4=0;double scale4=1;int32_t dot4=0;int32_t digits4=0;int32_t bad4=0;int32_t end4=0;for(int32_t j4=0;j4<15;j4=j4+1){int32_t ch4=Wire.read();if(end4==0){if(ch4==0){end4=1;}else{if(ch4==46 && dot4==0){dot4=1;}else{if(ch4>=48 && ch4<=57){value4=value4*10+ch4-48;digits4=digits4+1;if(dot4==1){scale4=scale4*10;}}else{bad4=1;}}}}}if(ack4==0 && count4==16 && status4==1 && digits4>0 && bad4==0 && end4==1){reading4=value4/scale4;if(reading4<500.0 || reading4>1500.0){excursions=excursions+1;}}else{faults=faults+1;}int32_t count5=Wire.requestFrom(37,16);int32_t status5=Wire.read();double value5=0;double scale5=1;int32_t dot5=0;int32_t digits5=0;int32_t bad5=0;int32_t end5=0;for(int32_t j5=0;j5<15;j5=j5+1){int32_t ch5=Wire.read();if(end5==0){if(ch5==0){end5=1;}else{if(ch5==46 && dot5==0){dot5=1;}else{if(ch5>=48 && ch5<=57){value5=value5*10+ch5-48;digits5=digits5+1;if(dot5==1){scale5=scale5*10;}}else{bad5=1;}}}}}if(ack5==0 && count5==16 && status5==1 && digits5>0 && bad5==0 && end5==1){reading5=value5/scale5;if(reading5<500.0 || reading5>1500.0){excursions=excursions+1;}}else{faults=faults+1;}int32_t count6=Wire.requestFrom(38,16);int32_t status6=Wire.read();double value6=0;double scale6=1;int32_t dot6=0;int32_t digits6=0;int32_t bad6=0;int32_t end6=0;for(int32_t j6=0;j6<15;j6=j6+1){int32_t ch6=Wire.read();if(end6==0){if(ch6==0){end6=1;}else{if(ch6==46 && dot6==0){dot6=1;}else{if(ch6>=48 && ch6<=57){value6=value6*10+ch6-48;digits6=digits6+1;if(dot6==1){scale6=scale6*10;}}else{bad6=1;}}}}}if(ack6==0 && count6==16 && status6==1 && digits6>0 && bad6==0 && end6==1){reading6=value6/scale6;if(reading6<500.0 || reading6>1500.0){excursions=excursions+1;}}else{faults=faults+1;}int32_t count7=Wire.requestFrom(39,16);int32_t status7=Wire.read();double value7=0;double scale7=1;int32_t dot7=0;int32_t digits7=0;int32_t bad7=0;int32_t end7=0;for(int32_t j7=0;j7<15;j7=j7+1){int32_t ch7=Wire.read();if(end7==0){if(ch7==0){end7=1;}else{if(ch7==46 && dot7==0){dot7=1;}else{if(ch7>=48 && ch7<=57){value7=value7*10+ch7-48;digits7=digits7+1;if(dot7==1){scale7=scale7*10;}}else{bad7=1;}}}}}if(ack7==0 && count7==16 && status7==1 && digits7>0 && bad7==0 && end7==1){reading7=value7/scale7;if(reading7<500.0 || reading7>1500.0){excursions=excursions+1;}}else{faults=faults+1;}int32_t count8=Wire.requestFrom(40,16);int32_t status8=Wire.read();double value8=0;double scale8=1;int32_t dot8=0;int32_t digits8=0;int32_t bad8=0;int32_t end8=0;for(int32_t j8=0;j8<15;j8=j8+1){int32_t ch8=Wire.read();if(end8==0){if(ch8==0){end8=1;}else{if(ch8==46 && dot8==0){dot8=1;}else{if(ch8>=48 && ch8<=57){value8=value8*10+ch8-48;digits8=digits8+1;if(dot8==1){scale8=scale8*10;}}else{bad8=1;}}}}}if(ack8==0 && count8==16 && status8==1 && digits8>0 && bad8==0 && end8==1){reading8=value8/scale8;if(reading8<5.0 || reading8>10.0){excursions=excursions+1;}}else{faults=faults+1;}int32_t count9=Wire.requestFrom(41,16);int32_t status9=Wire.read();double value9=0;double scale9=1;int32_t dot9=0;int32_t digits9=0;int32_t bad9=0;int32_t end9=0;for(int32_t j9=0;j9<15;j9=j9+1){int32_t ch9=Wire.read();if(end9==0){if(ch9==0){end9=1;}else{if(ch9==46 && dot9==0){dot9=1;}else{if(ch9>=48 && ch9<=57){value9=value9*10+ch9-48;digits9=digits9+1;if(dot9==1){scale9=scale9*10;}}else{bad9=1;}}}}}if(ack9==0 && count9==16 && status9==1 && digits9>0 && bad9==0 && end9==1){reading9=value9/scale9;if(reading9<5.0 || reading9>10.0){excursions=excursions+1;}}else{faults=faults+1;}alarm=0;if(faults>0 || excursions>0){alarm=1;}digitalWrite(A0,alarm);Serial1.println(faults);cycles=cycles+1;delay(100);}