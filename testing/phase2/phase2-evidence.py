"""Evidence packaging: classifications are separate from modeled runtime outcomes."""
import argparse,csv,hashlib,json,shutil,zipfile,re
from collections import Counter
from pathlib import Path
from datetime import datetime,timezone
import xml.etree.ElementTree as ET
from statistics import median
from lifecycle_reading_evidence import validate_reading_checkpoints
ROOT=Path(__file__).resolve().parent
def read(p):return json.loads(Path(p).read_text(encoding='utf-8-sig'))
def write(p,v):Path(p).write_text(json.dumps(v,indent=2,ensure_ascii=False),encoding='utf-8')
def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def reconcile_runtime_classes(older,current):
    def classes(path):
        with zipfile.ZipFile(path) as z:return {n:hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if n.endswith('.class')}
    old=classes(older);new=classes(current)
    changes=[dict(classFile=n,olderSha256=old.get(n),currentSha256=new.get(n)) for n in sorted(old.keys()|new.keys()) if old.get(n)!=new.get(n)]
    allowed=('com/indianservers/circuitssimulator/ui/SimulatorViewModel','com/indianservers/circuitssimulator/firmware/FirmwarePlayback')
    assert all(r['classFile'].startswith(allowed) for r in changes),('Unexpected runtime difference',changes)
    return dict(olderJar=str(older),olderJarSha256=sha(older),currentJarSha256=sha(current),unchangedClassFiles=sum(old.get(n)==v for n,v in new.items()),changes=changes,
        scope='Only the new UI playback helper and ViewModel wiring differ. Every other class bytecode must match. Earlier Phase 2 project validators exercise the identical core; the full current-source regression and new playback tests separately validate the addition. JAR metadata/timestamps are not class bytecode.')
def csvout(name,rows):
    if not rows:return
    with (ROOT/name).open('w',encoding='utf-8',newline='') as f:
        w=csv.DictWriter(f,fieldnames=list(rows[0]));w.writeheader();w.writerows(rows)
BLOCKERS={
    2:'Soil-water content, soil hydraulic response, irrigation flow and valve/pump feedback models are absent. A renamed thermal circuit cannot establish irrigation functionality.',
    4:'No validated barometric-pressure, rainfall and wind sensing models/topology. MPX5010 differential pressure is not a barometer and must not be relabeled as one.',
    11:'Cell electrochemistry/state estimation, balancing and an independently verified protection cutoff/feedback topology are absent. Ideal DC sources plus INA219 charge arithmetic do not establish a battery protection system.',
    14:'No independently validated conveyor/item-count/encoder/proximity production-flow design. Environmental monitoring alone does not establish factory production monitoring.'}
PRIOR={5:19,9:8,10:10,12:7,15:9,17:13,19:20,20:12}
SCOPE={
    1:'Seven LM75B zones, two BH1750FVI illuminance channels and one ADXL345 X channel; environmental excursion/status output.',
    3:'Seven thermal zones, two illuminance channels and SCD41 CO2/temperature/humidity, CRC-checked periodic polling and illustrative greenhouse condition request.',
    5:'Thermal zones, acceleration/gyro polling and smoothed vibration/trend thresholds. No motor degradation physics or validated remaining useful life.',
    6:'Eight thermal zones and two generic PIR inputs through transistor interfaces; intrusion/thermal status. Generic PIR is not manufacturer-verified.',
    7:'Thermal quorum and occupancy status, not a complete HVAC/building plant or access-control implementation.',
    8:'Environmental temperature, humidity, CO2 and illuminance monitoring only. No patient measurements or clinical/safety qualification.',
    9:'Calibrated pH/EC/DO polling. No turbidity, flow, treatment chemistry or fluid/process dynamics.',
    10:'Ten real shunt/resistor-load INA219 channels. No AC metering/power factor, PV curves or electrochemistry. Ten identical sensor parts do not establish sensor diversity.',
    12:'Independent temperature, CO2 and CO measurements with threshold indication; not all industrial gases or safety-qualified detection.',
    13:'Two independently triggered parking bays plus eight thermal channels. Diode-isolated echo OR and transistor level interface; generic 58 us/cm model, no acoustic crosstalk or ten-bay claim.',
    15:'Multi-zone heat and CO threshold indication; smoke/flame are absent, so full fire detection remains unmodeled.',
    16:'Illuminance-based status/lighting request with thermal and acceleration inhibit. Indicator LED, not a qualified mains lamp driver/photometric feedback system.',
    17:'Two actual MCUs exchange custom standard CAN frames. No OBD-II PID implementation, full CAN error/arbitration model or automotive qualification.',
    18:'Cold-chain thermal/light/tamper condition monitoring. No refrigeration plant, route/GNSS or logistics network execution.',
    19:'Acceleration/seismic thresholds and differential-pressure hydrostatic depth calculation. No flood/seismic propagation prediction.',
    20:'Complementary roll and one-dimensional integration. No complete robot mechanics, ToF/magnetometer/encoder/limit-switch feedback.'}
SENSORS={
    'I2C_TEMP_SENSOR':('semiconductor temperature','I2C'), 'BH1750_LIGHT':('photodiode illuminance','I2C'),
    'ADXL345_ACCEL':('MEMS acceleration','I2C'),'LSM6DSOX_IMU':('MEMS acceleration and angular rate','I2C'),
    'SCD41_CO2':('photoacoustic CO2 and integrated climate','I2C'),'INA219_POWER':('shunt current and voltage','I2C'),
    'EZO_PH':('electrochemical pH','I2C'),'EZO_EC':('electrical conductivity','I2C'),'EZO_DO':('dissolved oxygen probe','I2C'),
    'ZE07_CO':('electrochemical CO module','ADC'),'MPX5010_PRESSURE':('piezoresistive differential pressure','ADC'),
    'PIR_SENSOR':('generic passive infrared','GPIO'),'ULTRASONIC':('generic acoustic pulse ranging','PULSE_GPIO')}
def stage():
    boards=read(ROOT/'production-boards.json');manifest=[]
    components={p['kind']:p for p in read(ROOT/'production-components.json')}
    for b in boards:
        for n in range(1,21):
            folder=ROOT/'projects'/f"{b['id'].lower()}-{n:02d}"
            request=read(folder/'design-request.json')
            spec=dict(request,classification='NOT_EXECUTED',scope=SCOPE.get(n,BLOCKERS.get(n)),fullHardwareQualification=False,
                boardSource=b['sourceUrl'],boardManufacturerAudit='NOT_FULLY_REAUDITED',nativeFirmware='Separate native artifacts only when actually compiled',
                requiredMinimumSensorInstances=10,requiredIndividualSensorIsolation=True)
            if request['hasExecutableDesign']:
                circuit=read(folder/'circuit.json');sensors=[p for p in circuit['components'] if p['kind'] in SENSORS]
                assert len(sensors)>=10
                kinds=sorted({p['kind'] for p in sensors})
                spec.update(sensorInstances=len(sensors),uniqueSensorPartCount=len(kinds),uniqueSensorTechnologyCount=len({SENSORS[k][0] for k in kinds}),
                    sensorInterfaces=sorted({SENSORS[k][1] for k in kinds}),sensorKinds=kinds,
                    sensors=[dict(id=p['id'],kind=p['kind'],stimuli=p['parameters'],manufacturer=components[p['kind']].get('manufacturer'),part=components[p['kind']].get('part'),source=components[p['kind']].get('sourceUrl')) for p in sensors],
                    circuitSha256=sha(folder/'circuit.json'),firmwareRuntime='ARDUINO_SUBSET, not CPU emulation',
                    electricalAssumptions='External ideal rails; explicit wiring/pull-ups/decoupling/dividers/shunts as present. Educational peripheral supply loads are not datasheet power models.',
                    oracleSource='PhaseTwoLightValidation.kt / PhaseTwoParkingValidation.kt / AdvancedProjectChecks.kt for individually recorded suites; legacy validators for inherited subsets',
                    unexecutedFaults=['Full regulator-current-limit qualification','Physical thermal damage and semiconductor tolerance distributions','External hardware comparison'])
                dest=ROOT/'assets'/'phase2-projects'/folder.name;dest.mkdir(parents=True,exist_ok=True)
                shutil.copy2(folder/'circuit.json',dest/'circuit.json')
                manifest.append(dict(project=folder.name,board=b['id'],number=n,sha256=spec['circuitSha256']))
            else:
                spec.update(classification='BLOCKED_UNMODELED_DOMAIN_FUNCTIONS',blocker=BLOCKERS.get(n,'No implemented application design'),sensorInstances=0,
                    uniqueSensorPartCount=0,uniqueSensorTechnologyCount=0,sensorInterfaces=[])
            write(folder/'spec.json',spec)
    write(ROOT/'phase2-asset-manifest.json',manifest)
    matrix=[dict(kind=k,manufacturer=p.get('manufacturer'),part=p.get('part'),revision=p.get('revision'),url=p.get('sourceUrl'),
        evidence='BH1750FVI.pdf + light-datasheet-provenance.json; pages 1,2,4,5,6,7,14' if k=='BH1750_LIGHT' else 'Production metadata; retained Phase 1 source audits where applicable; not a complete fresh manufacturer audit',
        auditStatus='REVIEWED_NOMINAL_SUBSET' if k=='BH1750_LIGHT' else 'PARTIAL_OR_UNVERIFIED',limitations='; '.join(p.get('limitations',[]))) for k,p in components.items()]
    for missing in ['UV','rainfall','wind','soil moisture','ToF distance','magnetometer','rotary encoder','smoke','flame','leak detector','turbidity','water flow','GNSS','RFID','motor encoder','position-linked limit feedback']:
        matrix.append(dict(kind='REQUESTED_NOT_MODELED: '+missing,manufacturer='NOT_SELECTED',part='NOT_IMPLEMENTED',revision='',url='',evidence='No manufacturer-verified implementation claimed',auditStatus='BLOCKED_UNMODELED_DOMAIN_FUNCTIONS',limitations='No substitute sensor response is fabricated'))
    csvout('phase2-sensor-datasheet-matrix.csv',matrix)
    print(json.dumps(dict(boards=len(boards),requestedDesigns=len(boards)*20,executableCircuits=len(manifest),blockedDesigns=len(boards)*20-len(manifest))))

def suites(path):
    groups=[]
    for f in Path(path).glob('*.xml'):
        if f.name=='instrumented-device-results.xml':continue
        root=ET.parse(f).getroot();groups.extend([root] if root.tag=='testsuite' else root.findall('testsuite'))
    assert groups,('Missing JUnit',path)
    return groups
def junit(path):
    groups=suites(path)
    return {k:sum(int(s.attrib.get(k,0)) for s in groups) for k in ['tests','failures','errors','skipped']}
def case_map(path):
    return {(t.attrib.get('classname',s.attrib.get('name')),t.attrib['name']):t for s in suites(path) for t in s.findall('testcase')}

def finalize(android,regression,initial,affected,lifecycle):
    android=Path(android);manifest=read(ROOT/'phase2-asset-manifest.json');boards=read(ROOT/'production-boards.json')
    lifecycle=Path(lifecycle)
    for archive in [regression,initial,affected]:
        archive=Path(archive)
        assert not (archive/'FAILED.txt').exists(),str(archive)
        log=(archive/'gradle-run.log').read_text(encoding='utf-8-sig',errors='replace')
        assert 'BUILD SUCCESSFUL' in log and 'BUILD FAILED' not in log,str(archive)
    baseline=Path((ROOT.parent/'engineering-validation/domain-full-archive-path.txt').read_text(encoding='utf-8-sig').strip())
    expected_suites={s.attrib['name'] for s in suites(baseline)} | {'com.indianservers.circuitssimulator.'+n for n in ['Bh1750ModelTest','UartContinuousPrintTest','FirmwareExpressionRegressionTest','PhaseTwoInventoryTest','FirmwarePlaybackTest']}
    assert expected_suites == {s.attrib['name'] for s in suites(regression)},'Missing or stale regression suites'
    runtime_jar=ROOT.parent.parent/'app/build/intermediates/runtime_app_classes_jar/debug/bundleDebugClassesToRuntimeJar/classes.jar'
    assert sha(Path(regression)/'compiled-production-classes.jar')==sha(runtime_jar),'Production runtime changed after full regression'
    class_reconciliation=reconcile_runtime_classes(Path(affected)/'compiled-production-classes.jar',runtime_jar)
    write(ROOT/'jvm-runtime-class-reconciliation.json',class_reconciliation)
    probe=read(ROOT/'scd41-manufacturer-packet-probe.json');proof=read(ROOT/'scd41-manufacturer-probe-provenance.json')
    assert len(probe['vectors'])==3 and all(v['passed'] and v['expectedCrc']==v['actualCrc'] for v in probe['vectors'])
    assert all(probe['bh1750'][k] for k in ['passed','initialZero','earlyReadZero','resetZero'])
    assert proof['productionJarSha256'].lower()==sha(runtime_jar)
    assert proof['resultSha256'].lower()==sha(ROOT/'scd41-manufacturer-packet-probe.json')
    assert proof['probeSourceSha256'].lower()==sha(ROOT/'ManufacturerPacketProbe.java')
    for row in read(ROOT/'preserved-baseline-verification.json'):
        assert row['matches'] and row['baseline'].lower()==sha(Path(row['path']))
    regress=junit(regression);latest=case_map(initial);latest.update(case_map(affected))
    assert sum(c.endswith('.PhaseTwoProjectsTest') for c,n in latest)==len(manifest)
    assert all(t.find('failure') is None and t.find('error') is None and t.find('skipped') is None for t in latest.values()),'Unresolved project tests'
    assert not any(regress[k] for k in ['failures','errors','skipped'])
    android_stats=junit(android);assert android_stats['tests']==57 and not any(android_stats[k] for k in ['errors','skipped'])
    primary_cases=case_map(android)
    device=ET.parse(android/'instrumented-device-results.xml').getroot()
    assert device.find("./properties/property[@name='deviceRunComplete']").attrib['value']=='true'
    assert all(int(device.attrib[k])==android_stats[k] for k in ['tests','failures','errors','skipped'])
    assert {(t.attrib['classname'],t.attrib['name']) for t in device.findall('testcase')}==set(primary_cases)
    motion_identity=('com.indianservers.circuitssimulator.MotionSensorInstrumentedTest','gyroPropertiesEditRealMotionAndPreserveWiringWithUndo')
    motion=None;motion_cases={};motion_stats=dict(tests=0,failures=0,errors=0,skipped=0)
    if android_stats['failures']:
        assert android_stats['failures']==1
        failures={k:t for k,t in primary_cases.items() if t.find('failure') is not None}
        assert set(failures)=={motion_identity} and 'Failed waiting for PixelCopy!' in ''.join(failures[motion_identity].find('failure').itertext())
        assert (android/'FAILED.txt').exists(),'Raw primary failure must remain explicitly marked'
        motion=Path(read(android/'motion-recovery.json')['archive'])
        motion_stats=junit(motion);assert motion_stats==dict(tests=1,failures=0,errors=0,skipped=0)
        motion_cases=case_map(motion);assert set(motion_cases)=={motion_identity}
        motion_xml=ET.parse(motion/'instrumented-device-results.xml').getroot()
        assert motion_xml.find("./properties/property[@name='deviceRunComplete']").get('value')=='true'
        assert all(int(motion_xml.get(k))==motion_stats[k] for k in ['tests','failures','errors','skipped'])
        assert re.search(r'OK \(1 tests?\)',(motion/'android-motion-recovery.log').read_text(encoding='utf-8-sig'))
        method=read(motion/'run-method.json')
        assert method['status']=='PASSED_UNCHANGED_PIXELCOPY_RECOVERY' and Path(method['primaryArchive']).resolve()==android.resolve() and not method['apkInstallPerformed']
        for name in ['app-debug.apk','app-debug-androidTest.apk','MotionSensorInstrumentedTest.kt']:assert sha(android/name)==sha(motion/name)
        motion_review=read(ROOT/'motion-visual-review.json')
        assert motion_review['status']=='PASSED_VISUAL_REVIEW' and Path(motion_review['archive']).resolve()==motion.resolve()
        assert motion_review['imageSha256']==sha(motion/'visual-qa/motion-sensor-pins.png')
    target_stats=junit(lifecycle)
    assert target_stats==dict(tests=11,failures=0,errors=0,skipped=0)
    target_xml=ET.parse(lifecycle/'instrumented-device-results.xml').getroot()
    assert target_xml.find("./properties/property[@name='deviceRunComplete']").attrib['value']=='true'
    target_cases=case_map(lifecycle)
    target_identity=('com.indianservers.circuitssimulator.PhaseTwoLifecycleInstrumentedTest','tenSensorFirmwareSurvivesRotationRecreationAndBackground')
    expected_target={target_identity}|{('com.indianservers.circuitssimulator.DomainProjectsInstrumentedTest',n) for n in ['airQuality','waterTreatment','heatAndCoDetection','solarAndBattery','robotics','vehicleCan','chemicalProcess','aquaculture','predictiveMaintenance','disasterDetection']}
    assert set(target_cases)==expected_target and set(target_cases)&set(case_map(android))=={target_identity}
    assert 'OK (11 tests)' in (lifecycle/'android-lifecycle-rerun.log').read_text(encoding='utf-8-sig')
    review=read(ROOT/'lifecycle-visual-review.json')
    assert review['status']=='PASSED_TARGETED_VISUAL_REVIEW' and Path(review['targetedArchive']).resolve()==lifecycle.resolve()
    assert review['primaryTestApkSha256']==sha(android/'app-debug-androidTest.apk')
    assert len(review['reviewedImageSha256'])==4
    for name,digest in review['reviewedImageSha256'].items():assert sha(lifecycle/'visual-qa'/name)==digest
    assert sha(android/'app-debug.apk')==sha(lifecycle/'app-debug.apk')
    for archive in [android,lifecycle]+([motion] if motion else []):
        for row in read(archive/'device-installed-apk-hashes.json'):
            assert row['matchesArchivedBuild'] and sha(archive/row['file'])==row['sha256']==sha(archive/row['archivedBuild'])
        with zipfile.ZipFile(archive/'app-debug-androidTest.apk') as z:
            for item in manifest:assert hashlib.sha256(z.read('assets/phase2-projects/'+item['project']+'/circuit.json')).hexdigest()==item['sha256']
    assert not set(motion_cases)&set(target_cases)
    accepted_cases=dict(primary_cases);accepted_cases.update(motion_cases);accepted_cases.update(target_cases)
    assert len(accepted_cases)==67 and all(t.find('failure') is None and t.find('error') is None and t.find('skipped') is None for t in accepted_cases.values()),'Every accepted method needs an actual passing callback'
    write(ROOT/'android-acceptance-reconciliation.json',dict(primaryArchive=str(android),targetedArchive=str(lifecycle),
        rawPrimaryMethods=android_stats['tests'],rawTargetedMethods=target_stats['tests'],acceptedUniqueMethods=len(accepted_cases),
        motionRecoveryArchive=str(motion) if motion else None,rawMotionRecoveryMethods=motion_stats['tests'],recoveredPrimaryFailure=list(motion_identity) if motion else None,
        supersededPrimaryMethod=list(target_identity),reason='Primary numeric-only lifecycle superseded by real-navigation review. One raw PixelCopy failure is retained and accepted only after the unchanged method actually passes separately. Supplemental run executes all ten original domain groups. No raw XML is edited or synthesized.',
        accepted=[dict(className=c,method=n,evidence=str(motion if (c,n) in motion_cases else lifecycle if (c,n) in target_cases else android)) for c,n in sorted(accepted_cases)]))
    native_rows=read(ROOT/'native-compilation-results.json')
    rows=[];electrical=[];firmware=[];scenario_totals=Counter();recorded_assertions=Counter();placements=0;individual=0;reconciled=[]
    def canonical(path):
        data=read(path);data.pop('savedAtUtc',None)
        return json.dumps(data,sort_keys=True,separators=(',',':'))
    for b in boards:
        for n in range(1,21):
            folder=ROOT/'projects'/f"{b['id'].lower()}-{n:02d}";spec=read(folder/'spec.json');r=dict(project=folder.name,board=b['id'],number=n,application=spec['application'])
            if not spec['hasExecutableDesign']:
                rows.append(dict(r,classification='BLOCKED_UNMODELED_DOMAIN_FUNCTIONS',modeledJvm='NOT_EXECUTED',android='NOT_EXECUTED',sensorInstances=0,uniqueSensorPartCount=0,uniqueSensorTechnologyCount=0,sensorInterfaces='',independentlyVerifiedSensors=0,jvmScenarios=0,androidScenarios=0,recordedJvmAssertions=0,recordedAndroidAssertions=0,jvmElapsedMillis=None,androidElapsedMillis=None,blocker=spec['blocker']))
                firmware.append(dict(project=folder.name,runtime='NOT_EXECUTED',controllerCount=0,jvm='NOT_EXECUTED',android='NOT_EXECUTED',native='NOT_EXECUTED',nativeCpuExecution='NOT_EXECUTED',nativeFloatingPointParity='NOT_EXECUTED',nativeEvidence='',circuitSha256='',scope=spec['blocker']))
                electrical.append(dict(project=folder.name,scenario='NOT_EXECUTED_NO_CIRCUIT',status='BLOCKED_UNMODELED_DOMAIN_FUNCTIONS',assertions=0,mechanism=spec['blocker'],evidence=str(folder/'spec.json')))
                continue
            j=read(folder/'jvm-results.json');d=read(android/'project-results'/f'phase2-{folder.name}-results.json')
            assert j['status']==d['status']=='PASSED_MODELED_SCOPE',(folder.name,j['status'],d['status'])
            assert j['circuitSha256']==d['circuitAssetSha256']==sha(folder/'circuit.json')==spec['circuitSha256'],folder.name
            def count(result):return len(result.get('scenarios',result.get('scenarioRecords',result.get('legacyHarnessScenarioRecords',[]))))
            if j.get('assertions') is None:
                source=Path(regression)/'domain-project-results'/f"{b['id'].lower()}-{PRIOR[n]:02d}"
                counter=read(source/'assertion-count.json')
                assert counter['status']=='PASSED_DOMAIN_MONITORING' and counter['assertions']>0
                assert counter['circuitSha256']==sha(source/'circuit.json')
                assert canonical(source/'circuit.json')==canonical(folder/'circuit.json'),folder.name
                assert len(counter['scenarios'])==count(j)
                j['assertions']=counter['assertions']
                reconciled.append(dict(project=folder.name,count=counter['assertions'],counterEvidence=str(source/'assertion-count.json'),
                    canonicalCircuitSha256=hashlib.sha256(canonical(folder/'circuit.json').encode()).hexdigest(),method='Current-source Phase 1 regression on the identical canonical circuit, ignoring only save timestamp; original raw result remains unchanged'))
            for platform,result in [('jvm',j),('android',d)]:
                scenario_totals[platform]+=count(result)
                recorded_assertions[platform]+=result.get('assertions') or 0
                if 'scenarios' in result:
                    assert all(s['status']=='PASSED_MODELED_SCOPE' and all(a['passed'] for a in s['assertions']) for s in result['scenarios'])
            verified=j.get('independentlyVerifiedSensorInstances')
            classification='PASSED_MODELED_SCOPE' if verified==10 and spec['uniqueSensorTechnologyCount']>=2 and n not in [3,7] else 'MODELED_SUBSYSTEM_ONLY'
            placements+=spec['sensorInstances'];individual+=verified or 0
            rows.append(dict(r,classification=classification,modeledJvm=j['status'],android=d['status'],sensorInstances=spec['sensorInstances'],uniqueSensorPartCount=spec['uniqueSensorPartCount'],
                uniqueSensorTechnologyCount=spec['uniqueSensorTechnologyCount'],sensorInterfaces=';'.join(spec['sensorInterfaces']),independentlyVerifiedSensors=verified,
                jvmScenarios=count(j),androidScenarios=count(d),recordedJvmAssertions=j.get('assertions'),recordedAndroidAssertions=d.get('assertions'),jvmElapsedMillis=j['elapsedMillis'],androidElapsedMillis=d['elapsedMillis'],blocker=spec['scope']))
            spec['classification']=classification;spec['jvmEvidence']='jvm-results.json';spec['androidEvidence']=str(android/'project-results'/f'phase2-{folder.name}-results.json');write(folder/'spec.json',spec)
            for s in d.get('scenarios',[]):
                electrical.append(dict(project=folder.name,scenario=s['scenario'],status=s['status'],assertions=len(s['assertions']),
                    mechanism='See actual node/load/read/timeout/protocol assertions and topology in raw project result',evidence=str(android/'project-results'/f'phase2-{folder.name}-results.json')))
            for s in d.get('scenarioRecords',[]):
                electrical.append(dict(project=folder.name,scenario=s['scenario'],status=d['status'],assertions='NOT_SEPARATELY_COUNTED_PER_SCENARIO',
                    mechanism='Actual shared AdvancedProjectChecks assertions; expected/observed maps and check labels retained in raw record',evidence=str(android/'project-results'/f'phase2-{folder.name}-results.json')))
            for s in d.get('legacyHarnessScenarioRecords',[]):
                electrical.append(dict(project=folder.name,scenario=s if isinstance(s,str) else json.dumps(s),status=d['status'],assertions='NOT_SEPARATELY_COUNTED_PER_SCENARIO',
                    mechanism='Inherited shared validator; project-level dynamic check count retained; full numeric per-assertion trace not available',evidence=str(android/'project-results'/f'phase2-{folder.name}-results.json')))
            compiled=[r for r in native_rows if r['project']==folder.name]
            firmware.append(dict(project=folder.name,runtime='ARDUINO_SUBSET',controllerCount=len(read(folder/'circuit.json')['firmware']),jvm=j['status'],android=d['status'],
                native=';'.join(f"{r['controller']}:{r['status']}" for r in compiled) if compiled else 'NOT_EXECUTED_SDK_NOT_PROVISIONED',
                nativeCpuExecution='NOT_EXECUTED',nativeFloatingPointParity='NOT_EXECUTED',nativeEvidence='native-compilation-results.json' if compiled else '',
                circuitSha256=sha(folder/'circuit.json'),scope=spec['scope']))
    csvout('phase2-project-results.csv',rows);csvout('phase2-electrical-validation.csv',electrical);csvout('phase2-firmware-validation.csv',firmware)
    write(ROOT/'jvm-assertion-reconciliation.json',reconciled)
    csvout('phase2-board-results.csv',[dict(board=b['id'],requestedProjects=20,**{k:sum(r['classification']==k for r in rows if r['board']==b['id']) for k in ['PASSED_MODELED_SCOPE','MODELED_SUBSYSTEM_ONLY','BLOCKED_UNMODELED_DOMAIN_FUNCTIONS']},
        manufacturerAudit='PARTIAL_NOT_FULL_HARDWARE_QUALIFICATION') for b in boards])
    web=read(ROOT/'phase2-web-results/same-project-import-results.json')
    assert len(web['records'])==len(rows)
    for r in web['records']:
        if 'androidCircuitSha256' in r:assert r['androidCircuitSha256']==sha(ROOT/'projects'/r['project']/'circuit.json')
    native=read(ROOT/'native-compilation-results.json')
    for r in native:
        assert r['status']=='COMPILED_NATIVE' and not r['semanticWarnings'],r
        assert sha(ROOT/r['nativeSketch'])==r['nativeSketchSha256']
        c=read(ROOT/'projects'/r['project']/'circuit.json')
        source=next(f['source'] for f in c['firmware'] if f['boardId']==r['controller'])
        assert hashlib.sha256(source.encode()).hexdigest()==r['interpretedSourceSha256']
        for name,digest in r['outputs'].items():assert sha(ROOT/'native-builds'/Path(r['nativeSketch']).parent.name/name)==digest
    web_unit=read(ROOT/'phase2-web-results/web-unit-results.json')
    browser=read(ROOT/'phase2-web-results/browser-results.json')
    lifecycle_result=read(lifecycle/'phase2-lifecycle-results.json')
    assert lifecycle_result['status']=='PASSED_MODELED_SCOPE'
    assert all(lifecycle_result[k] for k in ['realAppNavigation','workspaceVisibleAfterEveryTransition','foregroundWindowVerified','actualRotation','activityRecreation','backgroundForeground'])
    lifecycle_readings=validate_reading_checkpoints(lifecycle_result)
    legacy=read(lifecycle/'domain-project-results.json');legacy_summary=read(lifecycle/'legacy-domain-summary.json')
    expected_legacy={f"{b['id'].lower()}-{n:02d}" for b in boards for n in [7,8,9,10,12,13,16,18,19,20]}
    assert len(legacy)==len(expected_legacy) and {r['project'] for r in legacy}==expected_legacy
    def legacy_canonical(p):
        d=read(p);d.pop('savedAtUtc',None);return d
    with zipfile.ZipFile(lifecycle/'app-debug-androidTest.apk') as z:
        for r in legacy:
            assert r['status']=='PASSED_DOMAIN_MONITORING' and r['sensorCount']==10 and r['assertions']>0
            payload=z.read('assets/domain-projects/'+r['project']+'/circuit.json')
            assert hashlib.sha256(payload).hexdigest()==r['circuitAssetSha256']
            prior=Path(regression)/'domain-project-results'/r['project'];counter=read(prior/'assertion-count.json')
            assert counter['circuitSha256']==sha(prior/'circuit.json') and counter['assertions']==r['assertions']
            assert len(counter['scenarios'])==len(r['scenarios'])
            assert legacy_canonical(prior/'circuit.json')==legacy_canonical(lifecycle/'domain-circuits'/r['project']/'circuit.json')
    timings={}
    for platform in ['jvm','android']:
        values=[r[platform+'ElapsedMillis'] for r in rows if r[platform+'ElapsedMillis'] is not None]
        assert len(values)==len(manifest) and all(v>0 for v in values)
        timings[platform]=dict(projectSuites=len(values),medianMillis=median(values),maximumMillis=max(values),sumOfPerProjectElapsedMillis=sum(values))
    timings['scope']='Actual end-to-end project validation suites, including fault loops/export/report I/O. Parallel elapsed sums are not campaign wall time or real-time simulation speed; host workload was not isolated.'
    summary=dict(recordedUtc=datetime.now(timezone.utc).isoformat(),boards=len(boards),requestedProjects=len(rows),executedModeledProjects=len(manifest),sensorPlacements=placements,
        requestedMinimumSensorPlacements=len(rows)*10,sensorPlacementShortfall=len(rows)*10-placements,executedPlacementsWithoutFullIsolationEvidence=placements-individual,
        individuallyVerifiedSensorInstances=individual,classificationCounts={k:sum(r['classification']==k for r in rows) for k in ['PASSED_FULL_SCOPE','PASSED_MODELED_SCOPE','MODELED_SUBSYSTEM_ONLY','BLOCKED_UNSUPPORTED_HARDWARE','BLOCKED_UNMODELED_DOMAIN_FUNCTIONS','FAILED_VERIFICATION','NOT_EXECUTED']},projectScenarios=dict(scenario_totals),recordedProjectAssertions=dict(recorded_assertions),
        sensorTechnologyCounting='Distinct modeled measurement families, with physical sensor instances and part kinds counted separately. An integrated multi-output IC counts as one placement. These counts do not certify die-level transduction technologies or physical hardware.',
        assertionCountingScope='Actual explicit assertion records in new scenarios; dynamically counted check calls in inherited validators. Older null counts are reconciled only against current-source regression runs with identical canonical circuits; raw runs remain unchanged. Engine-internal guards/JUnit infrastructure are outside project counts.',
        phase1JvmRegression=regress,phase2LatestUniqueProjectTests=sum(c.endswith('.PhaseTwoProjectsTest') for c,n in latest),android=android_stats,
        androidArchive=str(android.resolve()),regressionArchive=str(Path(regression).resolve()),initialProjectArchive=str(Path(initial).resolve()),affectedProjectArchive=str(Path(affected).resolve()),
        webImportCounts=dict(Counter(r['status'] for r in web['records'])),webParity='NOT_ESTABLISHED: actual production importer rejects Android schema',
        nativeCompilationCounts=dict(Counter(r['status'] for r in native)),nativeCpuExecution='NOT_EXECUTED',nativeFloatingPointParity='NOT_EXECUTED',
        nativeCompiledProjects=len({r['project'] for r in native if r['status']=='COMPILED_NATIVE'}),executedControllerAttachments=sum(f['controllerCount'] for f in firmware),
        webUnitRegression={k:web_unit.get(k) for k in ['success','numTotalTests','numPassedTests','numFailedTests']},
        browserEvidence='phase2-web-results/browser-results.json; live session backend CORS failures remain unresolved',androidLifecycle=lifecycle_result,
        browserCounts=dict(Counter(r['status'] for r in browser['results'])),
        browserPageErrorEvents=sum(len(r.get('exceptions',[])) for r in browser['results']),
        androidTargetedRerun=target_stats,androidMotionRecovery=motion_stats,motionRecoveryArchive=str(motion) if motion else None,
        androidAcceptedUniqueMethods=len(accepted_cases),androidRawMethodExecutions=android_stats['tests']+target_stats['tests']+motion_stats['tests'],
        androidRuntimeConfiguration=read(android/'runtime-memory-profile.json'),
        androidLifecycleReadingEvidence=lifecycle_readings,
        androidSupersededNumericOnlyMethod=list(target_identity),lifecycleRerunArchive=str(lifecycle.resolve()),
        supplementalLegacyDomainRegression=legacy_summary,androidExecutionCountingScope='Primary57, focused unchanged motion recovery1, supplemental11. Raw primary PixelCopy failure retained; accepted unique methods require actual passing callbacks. Earlier prototypes remain separate.',
        runtimeClassReconciliation='jvm-runtime-class-reconciliation.json: all earlier core class bytes match; only the new playback helper and ViewModel wiring differ',
        validationSuiteTimings=timings,
        manufacturerAudit='PARTIAL, explicit parameter traceability for reviewed subsets',fullHardwareQualification='NOT_ESTABLISHED',physicalDeviceTests='NOT_EXECUTED',
        fullApplicationAcceptance='NOT_MET: explicit unresolved domain/platform/qualification requirements remain')
    components={p['kind']:p for p in read(ROOT/'production-components.json')}
    references=[]
    for p in [ROOT/'BH1750FVI.pdf']+sorted((ROOT.parent/'engineering-validation/datasheets').glob('*.pdf')):
        references.append(dict(file=p.name,sha256=sha(p),retainedLocalPath=str(p.resolve()),
            sourceUrl='https://mm.digikey.com/Volume0/opasdata/d220001/medias/docus/6165/bh1750fvi-e.pdf' if p.name=='BH1750FVI.pdf' else components.get(p.stem,{}).get('sourceUrl'),
            inventory='See production component metadata, sensor matrix and retained source provenance; no unverified URL association is invented'))
    write(ROOT/'manufacturer-datasheet-index.json',references)
    archive=ROOT/'phase2-projects.zip'
    with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
        z.writestr('README.txt',f"{len(rows)} requested application slots; {len(manifest)} executed modeled circuits. Blocked slots contain explicit specifications, not fictitious firmware/circuits. See per-project classification and scope. Full application acceptance is not established.\n")
        for p in (ROOT/'projects').rglob('*'):
            if p.is_file():z.write(p,'projects/'+p.relative_to(ROOT/'projects').as_posix())
        for name in ['phase2-project-results.csv','phase2-board-results.csv','phase2-sensor-datasheet-matrix.csv','phase2-electrical-validation.csv','phase2-firmware-validation.csv','manufacturer-datasheet-index.json','light-datasheet-provenance.json','EXECUTION-PLAN.md','phase2-bugs-fixed.md','phase2-known-limitations.md','native-compilation-results.json','scd41-manufacturer-packet-probe.json','scd41-manufacturer-probe-provenance.json','jvm-assertion-reconciliation.json','production-boards.json','production-components.json']:
            z.write(ROOT/name,name)
        for directory in ['native-sketches','native-builds','phase2-web-results']:
            for p in (ROOT/directory).rglob('*'):
                if p.is_file():z.write(p,p.relative_to(ROOT).as_posix())
        if (ROOT/'phase2-final-report.md').exists():z.write(ROOT/'phase2-final-report.md','phase2-final-report.md')
        for name in ['toolchains/provenance.json','native-provision.log','native-integrity-verification.txt','verify-native.py','compile-native.py','ManufacturerPacketProbe.java','phase2-parameter-traceability.csv','preserved-baseline-verification.json','jvm-runtime-class-reconciliation.json','android-acceptance-reconciliation.json','lifecycle-visual-review.json','lifecycle_reading_evidence.py','runtime-memory-profile.json']:
            z.write(ROOT/name,name)
        for p in (lifecycle/'visual-qa').glob('*.png'):z.write(p,'accepted-lifecycle-visuals/'+p.name)
        if motion:
            z.write(ROOT/'motion-visual-review.json','motion-visual-review.json')
            z.write(motion/'visual-qa/motion-sensor-pins.png','accepted-motion-capture/motion-sensor-pins.png')
    summary['projectsZipSha256']=sha(archive);write(ROOT/'phase2-verification-summary.json',summary)
    print(json.dumps(summary,indent=2))

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--stage',action='store_true');p.add_argument('--android');p.add_argument('--regression');p.add_argument('--initial');p.add_argument('--affected');p.add_argument('--lifecycle');a=p.parse_args()
    if a.stage:stage()
    else:finalize(a.android,a.regression,a.initial,a.affected,a.lifecycle)
