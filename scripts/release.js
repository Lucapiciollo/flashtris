#!/usr/bin/env node
/**
 * Bumps the app version (versionCode +1, versionName patch +1) in app/build.gradle,
 * then builds both the release APK and the release AAB.
 *
 * Usage: npm run release
 */
'use strict';

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const rootDir = path.resolve(__dirname, '..');
const gradleFile = path.join(rootDir, 'app', 'build.gradle');

function bumpVersion() {
    const content = fs.readFileSync(gradleFile, 'utf8');

    const versionCodeMatch = content.match(/versionCode\s+(\d+)/);
    const versionNameMatch = content.match(/versionName\s+'([\d.]+)'/);

    if (!versionCodeMatch || !versionNameMatch) {
        throw new Error('Could not find versionCode/versionName in app/build.gradle');
    }

    const oldVersionCode = parseInt(versionCodeMatch[1], 10);
    const newVersionCode = oldVersionCode + 1;

    const oldVersionName = versionNameMatch[1];
    const parts = oldVersionName.split('.').map(Number);
    parts[parts.length - 1] += 1;
    const newVersionName = parts.join('.');

    let updated = content.replace(/versionCode\s+\d+/, `versionCode ${newVersionCode}`);
    updated = updated.replace(/versionName\s+'[\d.]+'/, `versionName '${newVersionName}'`);

    fs.writeFileSync(gradleFile, updated, 'utf8');

    console.log(`Version bumped: versionCode ${oldVersionCode} -> ${newVersionCode}, versionName ${oldVersionName} -> ${newVersionName}`);
    return { newVersionCode, newVersionName };
}

function runGradle(task) {
    const gradlew = process.platform === 'win32' ? '.\\gradlew.bat' : './gradlew';
    console.log(`\n> ${gradlew} ${task}\n`);
    execSync(`${gradlew} ${task} --console=plain`, { cwd: rootDir, stdio: 'inherit' });
}

function main() {
    const { newVersionCode, newVersionName } = bumpVersion();

    runGradle('assembleRelease');
    runGradle('bundleRelease');

    console.log(`\nDone. Version ${newVersionName} (code ${newVersionCode}) built.`);
    console.log('APK: app/build/outputs/apk/release/');
    console.log('AAB: app/build/outputs/bundle/release/app-release.aab');
}

main();
