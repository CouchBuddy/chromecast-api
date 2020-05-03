#import "ChromecastApiPlugin.h"
#if __has_include(<chromecast_api/chromecast_api-Swift.h>)
#import <chromecast_api/chromecast_api-Swift.h>
#else
// Support project import fallback if the generated compatibility header
// is not copied when this plugin is created as a library.
// https://forums.swift.org/t/swift-static-libraries-dont-copy-generated-objective-c-header/19816
#import "chromecast_api-Swift.h"
#endif

@implementation ChromecastApiPlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  [SwiftChromecastApiPlugin registerWithRegistrar:registrar];
}
@end
