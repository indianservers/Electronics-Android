"""Write the final review notes after the evidence verifier succeeds."""
import csv, hashlib, json, re, zipfile
from collections import Counter
from pathlib import Path
root=Path(__file__).resolve().parent
summary=json.loads((root/'domain-verification-summary.json').read_text())
assert summary['newDomainProjects']==190 and summary['android']['failures']==0
focused=Path(summary['android']['focusedArchive'])
bug=root/'domain-bug-report.txt'
text=bug.read_text(encoding='utf-8')
text=re.sub(r'DOMAIN-018:.*?(?=\n\nDOMAIN-019:)',
    'DOMAIN-018: Host UTP lost its ADB connection while the original device instrumentation continued. The cause of the reset is unknown. The original device invocation finished 50 tests with two artwork failures; all 190 domain and 380 legacy project executions passed. Raw host failure, device start/finish events, actual 50-tests/2-failed/0-ignored ending, direct JSON and installed APK hashes are retained. Both failing identities pass in the final 14-test affected-suite rerun. The new AtomicFile DeviceEvidenceListener was validated on-device: 14 exact identities match host JUnit, zero failures/errors/skips, deviceRunComplete=true. Latest results reconcile by exact identity without relabeling the original host invocation as successful.',text,flags=re.S)
text=text.replace('metadata compile/serialization is checked with the final listener smoke run.','metadata compile/serialization passed in the final isolated 14-test run.')
if 'DOMAIN-020:' not in text:
    text+='\n\nDOMAIN-020: The display fixture sent raw ASCII to SSD1306 and PCF8574/HD44780 devices and reused a 3.3 V OLED supply for a 5 V LCD. Replaced it with SSD1306 commands and HELLO glyph columns, plus a separate 5 V Uno LCD circuit with 4.7 kohm pull-ups, four-bit initialization and actual enable-edge nibble transfers. Assertions check the full 1024-byte framebuffer, visual adapter pixels and LCD DDRAM/adapter text. Screenshot capture now uses PixelCopy of the actual activity window. All seven artwork tests pass; production display semantics were preserved.\n\nDOMAIN-021: Another application took the shared emulator foreground during a UI rerun, removing this activity backing surface. The attempt is archived NOT-COMPLETE and excluded. Created a dedicated Android 15 API35 emulator, Codex_Circuits_Domain on emulator-5580, explicitly targeted for the 14-test affected rerun. All seven artwork and seven JSON tests pass. Other apps and the shared emulator remain running.\n'
bug.write_text(text,encoding='utf-8')
review=[
    'Final Android visual review: '+focused.name,
    'Reviewed all six native library gallery pages (149 component kinds), the OLED/LCD circuit captures, running motor, 90-degree servo and energized relay captures.',
    'New carrier bodies and terminal anchors render; these are simulator-defined carriers, not commercial breakout pinouts. Dense pin-label text remains small at gallery scale.',
    'The LCD capture visibly contains HELLO. The whole-circuit OLED capture is too small for human glyph-legibility qualification; its complete framebuffer and adapter pixels are asserted exactly in the device test.',
    'The artwork suite also checks all rotations, seven zoom scales, motor off/on/voltage/reversal/disconnection and servo commands at five angles.',
    'Measured emulator performance (software GPU, 2 vCPU, variable host load):'
]
for name,key in [('performance.json','nativeDrawMeanMs'),('compose-performance.json','drawMeanMs')]:
    rows=json.loads((focused/'visual-qa'/name).read_text())
    for row in rows:
        review.append(f"{name}: {row['components']} components, mean {row[key]:.2f} ms; sampled process PSS {row['processPssKb']} KiB"+(f"; animation recompositions {row['animationRecompositions']}" if 'animationRecompositions' in row else ''))
review.append('These are draw-time and sampled memory measurements, not measured display FPS, peak memory or hardware real-time guarantees.')
(root/'domain-visual-review.txt').write_text('\n'.join(review)+'\n',encoding='utf-8')
report=root/'domain-continuation-report.txt'
report.write_text(report.read_text(encoding='utf-8')+'\nFinal isolated Android rerun: host Gradle BUILD SUCCESSFUL, 14 tests / zero failures / zero skips. Persistent on-device JUnit matches every host identity and confirms run completion. Both APKs were read back from the isolated emulator and match their archived builds. Circuit assets match all 190 original project hashes.\nVisual and performance review: domain-visual-review.txt; raw images and measurements in the focused Android archive visual-qa directory.\n',encoding='utf-8')
(root/'CURRENT-STATUS.txt').write_text('Current verified continuation: domain-continuation-report.txt and domain-verification-summary.json.\nDeliverable: combined-projects.zip (380 projects, 20 per board; 190 new domain applications).\nEarlier engineering-report.txt and continuation-report.txt describe historical baseline scope and blocked applications; retain them as history. Raw failures and interrupted runs are retained and excluded from passing evidence unless resolved by an exact-identity rerun.\nFull hardware/physical qualification remains NOT_ESTABLISHED.\n',encoding='utf-8')
with zipfile.ZipFile(root/'combined-projects.zip') as z:
    circuits=[n for n in z.namelist() if n.endswith('/circuit.json')]
    assert len(circuits)==380
    counts=Counter(n.split('/')[0].rsplit('-',1)[0] for n in circuits)
    assert len(counts)==19 and set(counts.values())=={20}
    for n in circuits:
        folder=n.rsplit('/',1)[0]
        assert folder+'/spec.json' in z.namelist() and folder+'/result.txt' in z.namelist()
        assert any(p.startswith(folder+'/') and p.endswith('.ino') for p in z.namelist())
assert hashlib.sha256((root/'combined-projects.zip').read_bytes()).hexdigest()==summary['combinedZipSha256']
(root/'domain-work-state.txt').open('a',encoding='utf-8').write('\nCOMPLETED: 190 domain applications / 3610 scenarios and 380 legacy executions passed on Android. Latest JVM954 and Android50 exact test identities pass after retained affected-suite reruns. Final isolated Android14/14 passes with matching device callbacks and installed APK hashes. Verified 380-project ZIP, 19 boards x20, complete firmware/spec/result files; all six gallery pages reviewed. See CURRENT-STATUS.txt. No release build performed.\n')
print('Final package verified: 380 circuits, 19 boards x20, firmware/spec/result files present; ZIP hash matches summary.')
