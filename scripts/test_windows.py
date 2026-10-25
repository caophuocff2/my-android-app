"""Device regression: four independent edge drags and normal-icon relaunch."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--adb', required=True)
    parser.add_argument('--serial', default='emulator-5554')
    parser.add_argument('--package', default='com.roblox.clone02')
    parser.add_argument('--report', type=Path, required=True)
    args = parser.parse_args()
    if not re.fullmatch(r'com\.roblox\.clone(0[1-9]|1[01])', args.package):
        parser.error('Expected one of the eleven clone packages')

    def adb(*command):
        return subprocess.check_output([args.adb, '-s', args.serial, *command],
                                       text=True, encoding='utf-8', errors='replace', timeout=20)

    def state():
        mode = None
        for line in adb('shell', 'am', 'stack', 'list').splitlines():
            if line.startswith('Stack id='):
                mode = None
            if 'mWindowingMode=' in line:
                mode = re.search(r'mWindowingMode=(\w+)', line)[1]
            match = re.search(r'taskId=(\d+): ' + re.escape(args.package) +
                              r'/\S+ bounds=\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\].*visible=(\w+)', line)
            if match:
                return {'task': int(match[1]), 'mode': mode,
                        'bounds': [int(match[i]) for i in range(2, 6)], 'visible': match[6] == 'true'}
        return None

    def wait_for(check, description):
        deadline = time.monotonic() + 12
        last = None
        while time.monotonic() < deadline:
            last = state()
            if last and check(last):
                return last
            time.sleep(.4)
        raise AssertionError(f'{description}: {last}')

    current = wait_for(lambda s: s['visible'] and s['mode'] == 'freeform', 'Initial freeform window')
    results = []
    for side, index, delta in [('left', 0, -24), ('right', 2, 24), ('top', 1, -24), ('bottom', 3, 24)]:
        before = current['bounds'][:]
        left, top, right, bottom = before
        x, y = (left + right) // 2, (top + bottom) // 2
        if side == 'left': x = left - 4
        if side == 'right': x = right + 4
        if side == 'top': y = top - 4
        if side == 'bottom': y = bottom + 4
        end_x = x + delta if index in (0, 2) else x
        end_y = y + delta if index in (1, 3) else y
        desired = before[:]
        desired[index] += delta
        # Allow the grips to follow the last completed resize.
        time.sleep(1.5)
        adb('shell', 'input', 'swipe', str(x), str(y), str(end_x), str(end_y), '450')
        current = wait_for(lambda s: s['bounds'] == desired and s['mode'] == 'freeform', f'{side} drag')
        results.append({'edge': side, 'before': before, 'after': current['bounds'], 'passed': True})
        print(f'PASS: {side} edge -> {current["bounds"]}', flush=True)

    saved = current['bounds'][:]
    time.sleep(2)
    adb('shell', 'am', 'force-stop', args.package)
    adb('shell', 'am', 'start', '-n', args.package + '/com.roblox.client.startup.LauncherAliasMain')
    reopened = wait_for(lambda s: s['mode'] == 'freeform' and s['bounds'] == saved, 'Normal launcher size restoration')
    results.append({'test': 'normal_launcher_reopen', 'expected': saved, 'actual': reopened, 'passed': True})
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps({'package': args.package, 'tests': results, 'all_passed': True}, indent=2) + '\n')
    print('PASS: relaunch restores the last rectangle', flush=True)


if __name__ == '__main__':
    main()
