"""Check actual lifecycle records without generating or replacing device results."""
def validate_reading_checkpoints(result):
    assert result['allTenSensorReadingsVerifiedAfterEveryTransition'] is True
    expected={f't{i}':25.0+i*.5 for i in range(7)}
    expected.update(lux0=10.0,lux1=10.0,ax=0.0)
    labels=['portrait','landscape','foreground-restored','recreated','portrait-restored']
    rows=result['sensorReadingCheckpoints']
    assert len(rows)==len(labels) and [r['checkpoint'] for r in rows]==labels
    for row in rows:
        assert row['expected']==expected and set(row['observed'])==set(expected)
        for name,value in expected.items():
            actual=row['observed'][name]
            assert type(actual) in (float,int) and abs(actual-value)<=.0000001,(row['checkpoint'],name,actual,value)
    return dict(checkpoints=len(rows),sensorReadingsPerCheckpoint=len(expected),
        recordedNumericComparisons=len(rows)*len(expected),checkpointNames=labels,
        scope='Actual device expected/observed records; separate from Phase 2 project assertion totals')
