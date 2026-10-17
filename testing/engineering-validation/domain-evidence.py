"""Stage real circuits for Android, then verify raw evidence and package the combined 380 projects."""
import argparse, csv, hashlib, json, math, re, shutil, zipfile
from pathlib import Path
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
ROOT=Path(__file__).resolve().parent
REPO=ROOT.parent.parent
NUMBERS={7,8,9,10,12,13,16,18,19,20}
SOURCES=json.loads((ROOT/'domain-datasheet-audit.json').read_text(encoding='utf-8'))['sources']
def sha(path): return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def read(path): return json.loads(Path(path).read_text(encoding='utf-8-sig'))
def boards():
    with (ROOT/'board-capabilities.csv').open(encoding='utf-8-sig',newline='') as f: return list(csv.DictReader(f))
def xml_stats(directory,require_pass=True):
    # The device listener mirrors the host callbacks; never count that invocation twice.
    roots=[ET.parse(p).getroot() for p in Path(directory).glob('*.xml') if p.name!='instrumented-device-results.xml']
    suites=[s for root in roots for s in ([root] if root.tag=='testsuite' else root.findall('testsuite'))]
    assert suites, 'No raw JUnit XML'
    totals={key:sum(int(s.attrib.get(key,0)) for s in suites) for key in ['tests','failures','errors','skipped']}
    if require_pass: assert not any(totals[k] for k in ['failures','errors','skipped']), totals
    totals['suites']=len(suites)
    totals['summedSeconds']=sum(float(s.attrib.get('time',0)) for s in suites)
    return totals,suites
def spec(c,number,board):
    sensors=[p for p in c['components'] if p['kind'] in SOURCES and p['kind']!='MCP2515_CAN']
    assert len(sensors)>=10
    normal={}
    if number in [8,16,18]:
        for p in sensors:
            key={'EZO_PH':'ph','EZO_EC':'conductivity','EZO_DO':'oxygen'}[p['kind']]
            normal[p['id']]=dict(quantity=key,value=p['parameters'][key])
    elif number==10:
        normal={f'S{i}':{'amps':(18 if i<5 else 12)/100.1,'volts':(18 if i<5 else 12)*100/100.1,'ampsModelTolerance':.00011,'voltsModelTolerance':.0041,'powerModelToleranceW':.005} for i in range(10)}
    else:
        normal={p['id']:{'temperatureC':math.floor(p['parameters']['temperatureC']*2+.5)/2} for p in sensors if p['kind']=='I2C_TEMP_SENSOR'}
        if number in [7,9]: normal.update(co2Ppm=800,coPpm=0,coModelTolerancePpm=2)
        if number==12: normal.update(rollDegrees=0,positionM=0,kinematics='one-dimensional, initially stationary, aligned horizontal X')
        if number==19: normal.update(vibrationAmplitudeG=.03,vibrationFrequencyHz=2,alarm=0,prognostics='smoothed RMS and illustrative threshold-trend extrapolation; no RUL guarantee')
        if number==20: normal.update(pressureKpa=2,hydrostaticDepthM=2/9.80665,depthModelToleranceM=.01)
        if number==13: normal.update(canIdentifier=801,canBitRate=500000,spiClockHz=500000,remoteFaults=0,remoteExcursions=0)
    return dict(project=c['name'],board=board['board_id'],applicationNumber=number,scope='Documented domain-monitoring circuit and bounded firmware model',
        firmwareLanguage='ARDUINO_SUBSET; not native C++',modelSensors=len(sensors),normalExpected=normal,
        interfaces=['SPI','I2C','CAN','UART','GPIO'] if number==13 else ['I2C','UART','GPIO']+(['ADC'] if number in [7,9,20] else []),
        sensors=[dict(id=p['id'],kind=p['kind'],stimuli=p['parameters'],source=SOURCES[p['kind']]) for p in sensors],
        physicalAccuracyClaim=False,limitationsFile='scope.txt',boardSpecifications='Existing repository profile; board source audit remains separately qualified')
def stage():
    expected=[]
    for board in boards():
        for number in sorted(NUMBERS):
            project=f"{board['board_id'].lower()}-{number:02d}"
            folder=ROOT/'domain-projects'/project
            result=(folder/'result.txt').read_text(encoding='utf-8').splitlines()
            assert result[0]=='PASSED_DOMAIN_MONITORING',(project,result[:2])
            c=read(folder/'circuit.json')
            s=spec(c,number,board)
            s['executedJvmScenarios']=result[1:]
            s['circuitSha256']=sha(folder/'circuit.json')
            (folder/'spec.json').write_text(json.dumps(s,indent=2,ensure_ascii=False),encoding='utf-8')
            shutil.copy2(ROOT/'domain-model-scope.txt',folder/'scope.txt')
            dest=ROOT/'domain-assets'/'domain-projects'/project
            dest.mkdir(parents=True,exist_ok=True)
            for name in ['circuit.json','spec.json']: shutil.copy2(folder/name,dest/name)
            expected.append(dict(project=project,sha256=s['circuitSha256'],scenarios=len(result)-1))
    assert len(expected)==190
    (ROOT/'domain-jvm-circuit-manifest.json').write_text(json.dumps(expected,indent=2),encoding='utf-8')
    print(json.dumps(dict(stagedProjects=len(expected),jvmScenarioRecords=sum(p['scenarios'] for p in expected))))
def verify_jvm(jvm,jvm_focused=None):
    js,suites=xml_stats(jvm,require_pass=False)
    def cases(groups):
        return {(t.attrib.get('classname',s.attrib['name']),t.attrib['name']):t for s in groups for t in s.findall('testcase')}
    latest=cases(suites)
    focused_stats=None
    if jvm_focused:
        focused_stats,focused_suites=xml_stats(jvm_focused)
        focused_names={s.attrib['name']:int(s.attrib['tests']) for s in focused_suites}
        assert focused_names.get('com.indianservers.circuitssimulator.DomainMonitoringProjectsTest')==114
        assert focused_names.get('com.indianservers.circuitssimulator.ComponentTerminalsTest')==4
        latest.update(cases(focused_suites))
    assert all(t.find('failure') is None and t.find('error') is None and t.find('skipped') is None for t in latest.values()), 'Unresolved JVM regression'
    assert js['errors']==0, js
    current_stats=dict(tests=len(latest),failures=0,errors=0,skipped=0)

    names={s.attrib['name']:int(s.attrib['tests']) for s in suites}
    for name,count in [('AdvancedProjectsTest',380),('ComplexFiftyScenariosTest',50),('ChemistryProjectsTest',57),('DomainMonitoringProjectsTest',114),('VehicleProjectsTest',19)]:
        assert names.get('com.indianservers.circuitssimulator.'+name)==count,(name,names)
    return js,current_stats,focused_stats

def direct_android_stats(directory,focused=None):
    """Verify the original on-device run after a host ADB/UTP transport failure."""
    raw=(Path(directory)/'device-testrunner.log').read_text(encoding='utf-8-sig')
    end=re.findall(r'run finished: 50 tests, (\d+) failed, (\d+) ignored',raw)
    assert 'run started: 50 tests' in raw and len(end)==1 and int(end[0][1])==0, 'Device run incomplete/skipped'
    assert not re.search(r'TestRunner:\s+(?:skipped|ignored):',raw), 'Device test skip'
    started=re.findall(r'TestRunner:\s+started: ([\w]+)\((com\.indianservers\.circuitssimulator\.[\w]+)\)',raw)
    finished=re.findall(r'TestRunner:\s+finished: ([\w]+)\((com\.indianservers\.circuitssimulator\.[\w]+)\)',raw)
    assert len(started)==len(finished)==50 and len(set(finished))==50 and set(started)==set(finished), (len(started),len(finished))
    classes={'DomainProjectsInstrumentedTest','AdvancedProjectsInstrumentedTest','BoardHardwareInstrumentedTest','ProjectStoreInstrumentedTest','CircuitJsonInstrumentedTest','MotionSensorInstrumentedTest','PhysicalArtworkInstrumentedTest'}
    assert {c.rsplit('.',1)[-1] for _,c in finished}==classes
    assert (Path(directory)/'host-adb-failure.log').is_file()
    failed=re.findall(r'TestRunner:\s+failed: ([\w]+)\((com\.indianservers\.circuitssimulator\.[\w]+)\)',raw)
    assert len(failed)==int(end[0][0]) and set(failed)<=set(finished)
    unresolved=set(failed)
    focused_stats=None
    if focused:
        focused_stats,suites=xml_stats(focused)
        identities={(t.attrib['name'],t.attrib.get('classname',s.attrib['name'])) for s in suites for t in s.findall('testcase')}
        assert len(identities)==focused_stats['tests']==14 and identities<=set(finished)
        device=ET.parse(Path(focused)/'instrumented-device-results.xml').getroot()
        assert device.find("./properties/property[@name='deviceRunComplete']").attrib['value']=='true'
        assert int(device.attrib['tests'])==14 and all(int(device.attrib[k])==0 for k in ['failures','errors','skipped'])
        assert {(t.attrib['name'],t.attrib['classname']) for t in device.findall('testcase')}==identities
        unresolved-=identities
    assert not unresolved, ('Unresolved device tests',unresolved)
    return dict(tests=50,failures=0,errors=0,skipped=0,suites=7,originalRunFailures=len(failed),focusedRerun=focused_stats,
        focusedArchive=str(Path(focused).resolve()) if focused else None,
        evidence='RAW_DEVICE_RUN_PLUS_EXACT_IDENTITY_RERUNS',logSha256=sha(Path(directory)/'device-testrunner.log'))

def finalize(jvm,android,jvm_focused=None,android_logcat=False,android_focused=None):
    js,current_stats,focused_stats=verify_jvm(jvm,jvm_focused)
    ans=direct_android_stats(android,android_focused) if android_logcat else xml_stats(android)[0]
    rows=read(Path(android)/'domain-project-results.json')
    assert len(rows)==190 and len({r['project'] for r in rows})==190
    assert all(r['status']=='PASSED_DOMAIN_MONITORING' and r['sensorCount']>=10 and len(r['scenarios'])>=10 for r in rows)
    actual={r['project']:r for r in rows}
    manifest=read(ROOT/'domain-jvm-circuit-manifest.json')
    apk=Path(android)/'app-debug-androidTest.apk' if android_logcat else REPO/'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
    if android_logcat:
        installed=read(Path(android)/'device-installed-apk-hashes.json')
        assert len(installed)==2
        assert {r['package'] for r in installed}=={'com.indianservers.circuitssimulator','com.indianservers.circuitssimulator.test'}
        for r in installed:
            assert r['matchesArchivedBuild'] and sha(Path(android)/r['file'])==r['sha256']==sha(Path(android)/r['archivedBuild'])
    with zipfile.ZipFile(apk) as z:
        for item in manifest:
            digest=hashlib.sha256(z.read('assets/domain-projects/'+item['project']+'/circuit.json')).hexdigest()
            assert digest==item['sha256']==actual[item['project']]['circuitAssetSha256'],item['project']
    if android_focused:
        for r in read(Path(android_focused)/'device-installed-apk-hashes.json'):
            assert r['matchesArchivedBuild'] and sha(Path(android_focused)/r['file'])==r['sha256']==sha(Path(android_focused)/r['archivedBuild'])
        with zipfile.ZipFile(Path(android_focused)/'app-debug-androidTest.apk') as z:
            for item in manifest:
                assert hashlib.sha256(z.read('assets/domain-projects/'+item['project']+'/circuit.json')).hexdigest()==item['sha256']
        gallery=read(Path(android_focused)/'visual-qa/component-audit.json')
        assert len(gallery)==149 and all(p['nativeRenderVerified'] for p in gallery)
    legacy=read(Path(android)/'advanced-project-results.json')
    assert len(legacy)==380 and all(r['status']=='PASSED_MODELED_SCOPE' for r in legacy)
    combined=[]
    archive=ROOT/'combined-projects.zip'
    with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
        for name in ['domain-model-scope.txt','domain-datasheet-audit.json','board-capabilities.csv']:
            z.write(ROOT/name,name)
        z.writestr('README.txt','380 saved projects: 20 per supported board. Unpack and open a project circuit.json in the simulator. Firmware targets the repository Arduino subset. Each domain project has its own scope.txt and spec.json; shared model limitations and manufacturer sources are included at the ZIP root. Hardware/physical qualification is not established.\n')
        for board in boards():
            for number in range(1,21):
                project=f"{board['board_id'].lower()}-{number:02d}"
                domain=number in NUMBERS
                folder=ROOT/('domain-projects' if domain else 'projects')/project
                status='PASSED_DOMAIN_MONITORING' if domain else 'PASSED_MODELED_SCOPE'
                assert (folder/'result.txt').read_text(encoding='utf-8').splitlines()[0]==status
                for path in folder.iterdir():
                    if path.is_file():z.write(path,project+'/'+path.name)
                combined.append(dict(project=project,board=board['board_id'],number=number,status=status,jvm='PASSED',android='PASSED',scope='domain monitoring' if domain else 'thermal/occupancy subsystem',circuitSha256=sha(folder/'circuit.json')))
    with (ROOT/'combined-project-results.csv').open('w',encoding='utf-8',newline='') as f:
        writer=csv.DictWriter(f,fieldnames=list(combined[0]));writer.writeheader();writer.writerows(combined)
    summary=dict(evidenceVerifiedUtc=datetime.now(timezone.utc).isoformat(),jvmLatestCompleteRegression=current_stats,jvmFullSweep=js,jvmFocusedRerun=focused_stats,jvmEvidenceMethod="Full sweep followed by affected-suite reruns; latest result per test identity, raw runs retained",android=ans,uniqueCombinedProjects=len(combined),newDomainProjects=len(rows),
        newAndroidScenarioRecords=sum(len(r['scenarios']) for r in rows),jvmDomainScenarioRecords=sum(p['scenarios'] for p in manifest),
        legacyAndroidProjectRuns=len(legacy),androidEvidence='DIRECT_DEVICE_JSON_AND_TESTRUNNER_LOG' if android_logcat else 'DIRECT_DEVICE_JSON_AND_JUNIT',
        jvmArchive=str(Path(jvm).resolve()),jvmFocusedArchive=str(Path(jvm_focused).resolve()) if jvm_focused else None,androidArchive=str(Path(android).resolve()),
        domainCircuitHashesVerifiedAgainstTestApk=True,testApkSha256=sha(apk),combinedZipSha256=sha(archive),
        deviceInstalledApksVerified=android_logcat,
        fullHardwareAndPhysicalQualification='NOT_ESTABLISHED',
        limitations='domain-model-scope.txt; nominal bounded models, not physical or safety certification')
    (ROOT/'domain-verification-summary.json').write_text(json.dumps(summary,indent=2),encoding='utf-8')
    report=[
        'DOMAIN APPLICATION CONTINUATION — VERIFIED RESULTS',
        '',
        f"Built and executed {len(rows)} previously domain-blocked applications across {len(boards())} boards within the documented monitoring-model scope. Full hardware and physical qualification is not established.",
        'Families: air quality, water treatment, heat/CO detection, solar/battery monitoring, robotics, vehicle CAN, chemical process monitoring, aquaculture, predictive maintenance, disaster detection.',
        'Added manufacturer-datasheet-informed EZO-pH, EZO-EC, EZO-DO, SCD41, ZE07-CO, MPX5010, INA219, and MCP2515/MCP2562FD carrier models.',
        '',
        f"JVM: {current_stats['tests']} unique test identities pass. The full sweep and subsequent affected-suite reruns are retained separately; the latest result for each exact test identity is used.",
        f"Android: {ans['tests']} instrumented tests pass, with {len(rows)} direct domain application records / {summary['newAndroidScenarioRecords']} scenario records and {len(legacy)} earlier project executions.",
        'Every new domain record has at least ten active sensors and ten scenario checks. Circuit hashes match the JVM-exported circuits and the actual Android test APK assets.',
        f"Combined deliverable: {len(combined)} unique saved projects (20 per board): 190 domain applications plus 190 previously validated thermal/occupancy subsystem circuits.",
        '',
        'Fixes and qualification: domain-bug-report.txt; manufacturer revisions/links: domain-datasheet-audit.json; physical/model boundaries: domain-model-scope.txt.',
        'Local manufacturer PDF availability and hashes: datasheet-download-manifest.json. Failed downloads are recorded explicitly.',
        'Firmware runs the repository Arduino subset. Results validate nominal bounded monitoring/protocol models, not native CPU binaries, complete process physics, manufacturer accuracy, automotive compliance or safety certification.',
        'Runtime measurements come from an emulator under variable host load. They are not real-time performance guarantees. Heavy CAN allocation/GC was observed; scheduler capture now retains only the last 2500 frames during execution, matching its existing return contract. CAN validation is serial; other independent non-UI projects use three workers.',
        'Intentionally interrupted Android runs remain explicitly marked NOT-COMPLETE and are excluded from the verified results.',
        'Android transport provenance: '+('Host UTP failed after an ADB connection reset; the original device invocation continued and completed 50 tests with two artwork failures. Both failing identities pass in the subsequent 14-test affected-suite rerun, verified against host JUnit and persistent on-device JUnit callbacks. All 190 domain and 380 legacy project executions passed in the original device run. Raw failures and reruns are retained; the original host Gradle invocation failed.' if android_logcat else 'Raw JUnit XML and direct device project JSON.'),
        '',
        'Authoritative files: domain-verification-summary.json, combined-project-results.csv, combined-projects.zip.',
        f'Full JVM archive: {summary["jvmArchive"]}',
        f'Focused JVM archive: {summary["jvmFocusedArchive"]}',
        f'Android archive: {summary["androidArchive"]}',
        f'Focused Android archive: {ans.get("focusedArchive")}',
    ]
    (ROOT/'domain-continuation-report.txt').write_text('\n'.join(report)+'\n',encoding='utf-8')
    print(json.dumps(summary,indent=2))
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--stage',action='store_true');p.add_argument('--verify-jvm',action='store_true');p.add_argument('--jvm');p.add_argument('--android');p.add_argument('--jvm-focused');p.add_argument('--android-focused');p.add_argument('--android-logcat',action='store_true');a=p.parse_args()
    if a.stage:stage()
    elif a.verify_jvm and a.jvm:print(json.dumps(verify_jvm(a.jvm,a.jvm_focused),indent=2))
    elif a.jvm and a.android:finalize(a.jvm,a.android,a.jvm_focused,a.android_logcat,a.android_focused)
    else:p.error('Use --stage or --jvm DIRECTORY --android DIRECTORY')
