require 'json'

package = JSON.parse(File.read(File.join(__dir__, 'package.json')))

Pod::Spec.new do |s|
  s.name         = 'Stay22Capacitor'
  s.version      = package['version']
  s.summary      = package['description']
  s.license      = { :type => 'Commercial', :file => 'LICENSE' }
  s.homepage     = 'https://www.stay22.com'
  s.author       = { 'Stay22' => 'support@stay22.com' }
  s.source       = { :git => 'https://github.com/Stay22/stay22-capacitor-sdk.git', :tag => s.version.to_s }

  s.source_files = 'ios/Sources/Stay22CapacitorPlugin/**/*.swift'
  s.ios.deployment_target = '15.0'
  s.swift_version = '5.9'
  s.dependency 'Capacitor'

  # The SDK ships as a binary and Stay22 publishes no CocoaPod, so it is vendored into this
  # package rather than fetched — the same arrangement, and the same reason, as the Flutter
  # wrapper's podspec.
  s.vendored_frameworks = 'ios/Frameworks/Stay22SDK.xcframework'

  # Deliberately NOT `static_framework`. That flag describes how this pod builds, and
  # setting it while vendoring a dynamic framework is a known route to duplicate-symbol and
  # missing-embed failures. CocoaPods embeds and re-signs a vendored dynamic framework itself.
  s.pod_target_xcconfig = {
    'DEFINES_MODULE' => 'YES',
    # Stay22SDK ships no i386 slice.
    'EXCLUDED_ARCHS[sdk=iphonesimulator*]' => 'i386'
  }
end
