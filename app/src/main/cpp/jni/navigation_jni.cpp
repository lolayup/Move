#include <jni.h>
#include <string>
#include <vector>
#include "NavigationEngine.h"

using namespace move;

extern "C" JNIEXPORT jlong JNICALL
Java_com_khaled_move_navigation_NativeNavigationEngine_createNativeInstance(JNIEnv* env, jobject thiz) {
    return reinterpret_cast<jlong>(new NavigationEngine());
}

extern "C" JNIEXPORT void JNICALL
Java_com_khaled_move_navigation_NativeNavigationEngine_destroyNativeInstance(JNIEnv* env, jobject thiz, jlong ptr) {
    delete reinterpret_cast<NavigationEngine*>(ptr);
}

NavigationLocation mapLocation(JNIEnv* env, jobject locationObj) {
    jclass locClass = env->GetObjectClass(locationObj);

    jfieldID latField = env->GetFieldID(locClass, "latitude", "D");
    jfieldID lonField = env->GetFieldID(locClass, "longitude", "D");
    jfieldID accField = env->GetFieldID(locClass, "accuracyMeters", "Ljava/lang/Float;");
    jfieldID bearField = env->GetFieldID(locClass, "bearingDegrees", "Ljava/lang/Float;");
    jfieldID speedField = env->GetFieldID(locClass, "speedMetersPerSecond", "Ljava/lang/Float;");
    jfieldID timeField = env->GetFieldID(locClass, "timestampMillis", "J");

    NavigationLocation loc;
    loc.latitude = env->GetDoubleField(locationObj, latField);
    loc.longitude = env->GetDoubleField(locationObj, lonField);
    loc.timestampMillis = env->GetLongField(locationObj, timeField);

    auto getFloat = [&](jfieldID field) -> float {
        jobject floatObj = env->GetObjectField(locationObj, field);
        if (floatObj == nullptr) return 0.0f;
        jclass floatClass = env->FindClass("java/lang/Float");
        jmethodID floatValue = env->GetMethodID(floatClass, "floatValue", "()F");
        return env->CallFloatMethod(floatObj, floatValue);
    };

    loc.accuracyMeters = getFloat(accField);
    loc.bearingDegrees = getFloat(bearField);
    loc.speedMetersPerSecond = getFloat(speedField);

    return loc;
}

NavigationRoute mapRoute(JNIEnv* env, jobject routeObj) {
    jclass routeClass = env->GetObjectClass(routeObj);
    jfieldID geometryField = env->GetFieldID(routeClass, "geometry", "Ljava/util/List;");
    jfieldID distanceField = env->GetFieldID(routeClass, "distanceMeters", "D");
    jfieldID instructionsField = env->GetFieldID(routeClass, "instructions", "Ljava/util/List;");

    NavigationRoute route;
    route.distanceMeters = env->GetDoubleField(routeObj, distanceField);

    // Geometry
    jobject geometryList = env->GetObjectField(routeObj, geometryField);
    jclass listClass = env->FindClass("java/util/List");
    jmethodID sizeMethod = env->GetMethodID(listClass, "size", "()I");
    jmethodID getMethod = env->GetMethodID(listClass, "get", "(I)Ljava/lang/Object;");

    int size = env->CallIntMethod(geometryList, sizeMethod);
    route.geometry.reserve(size);
    jclass pointClass = env->FindClass("com/khaled/move/navigation/foot/route/RoutePoint");
    jfieldID pLatField = env->GetFieldID(pointClass, "latitude", "D");
    jfieldID pLonField = env->GetFieldID(pointClass, "longitude", "D");

    for (int i = 0; i < size; ++i) {
        jobject p = env->CallObjectMethod(geometryList, getMethod, i);
        route.geometry.push_back({ env->GetDoubleField(p, pLatField), env->GetDoubleField(p, pLonField) });
    }

    // Instructions
    jobject instructionsList = env->GetObjectField(routeObj, instructionsField);
    int instSize = env->CallIntMethod(instructionsList, sizeMethod);
    route.instructions.reserve(instSize);
    jclass instClass = env->FindClass("com/khaled/move/navigation/foot/instructions/NavigationInstruction");
    jfieldID iIdxField = env->GetFieldID(instClass, "routePointIndex", "I");
    jfieldID iTextField = env->GetFieldID(instClass, "text", "Ljava/lang/String;");

    for (int i = 0; i < instSize; ++i) {
        jobject inst = env->CallObjectMethod(instructionsList, getMethod, i);
        jstring textStr = (jstring)env->GetObjectField(inst, iTextField);
        const char* text = env->GetStringUTFChars(textStr, nullptr);
        route.instructions.push_back({ env->GetIntField(inst, iIdxField), std::string(text) });
        env->ReleaseStringUTFChars(textStr, text);
    }

    return route;
}

extern "C" JNIEXPORT void JNICALL
Java_com_khaled_move_navigation_NativeNavigationEngine_startNavigation(JNIEnv* env, jobject thiz, jlong ptr, jobject route, jobject initialLocation) {
    auto* engine = reinterpret_cast<NavigationEngine*>(ptr);
    engine->startNavigation(mapRoute(env, route), mapLocation(env, initialLocation));
}

extern "C" JNIEXPORT void JNICALL
Java_com_khaled_move_navigation_NativeNavigationEngine_stopNavigation(JNIEnv* env, jobject thiz, jlong ptr) {
    auto* engine = reinterpret_cast<NavigationEngine*>(ptr);
    engine->stopNavigation();
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_khaled_move_navigation_NativeNavigationEngine_processLocation(JNIEnv* env, jobject thiz, jlong ptr, jobject location) {
    auto* engine = reinterpret_cast<NavigationEngine*>(ptr);
    NavigationResult res = engine->processLocation(mapLocation(env, location));

    jclass resultClass = env->FindClass("com/khaled/move/navigation/NativeNavigationResult");
    // I(1) I(2) D(3) D(4) D(5) I(6) D(7) D(8) I(9) I(10) D(11) D(12) J(13) D(14) J(15) D(16) D(17) D(18) Z(19)
    jmethodID constructor = env->GetMethodID(resultClass, "<init>", "(IIDDDIDDIIDDJDJDDDZ)V");

    return env->NewObject(resultClass, constructor,
        (jint)res.status,
        (jint)res.movementState,
        (jdouble)res.progress.traveledDistanceMeters,
        (jdouble)res.progress.remainingDistanceMeters,
        (jdouble)res.progress.progressFraction,
        (jint)res.progress.currentRouteSegmentIndex,
        (jdouble)res.progress.snappedLocation.latitude,
        (jdouble)res.progress.snappedLocation.longitude,
        (jint)res.progress.currentInstructionIndex,
        (jint)res.progress.nextInstructionIndex,
        (jdouble)res.progress.distanceToNextInstructionMeters,
        (jdouble)res.progress.estimatedRemainingDurationSeconds,
        (jlong)res.progress.estimatedArrivalTimeMillis,
        (jdouble)res.speedMetersPerSecond,
        (jlong)res.elapsedTimeSeconds,
        (jdouble)res.snappedLocation.latitude,
        (jdouble)res.snappedLocation.longitude,
        (jdouble)res.navigationBearingDegrees,
        (jboolean)res.isRecalculating
    );
}

extern "C" JNIEXPORT void JNICALL
Java_com_khaled_move_navigation_NativeNavigationEngine_updateReroutingStatus(JNIEnv* env, jobject thiz, jlong ptr, jboolean isRerouting) {
    auto* engine = reinterpret_cast<NavigationEngine*>(ptr);
    engine->updateReroutingStatus(isRerouting);
}

extern "C" JNIEXPORT void JNICALL
Java_com_khaled_move_navigation_NativeNavigationEngine_addMetroStation(JNIEnv* env, jobject thiz, jlong ptr, jstring id, jstring name, jdouble lat, jdouble lon, jobjectArray lines) {
    auto* engine = reinterpret_cast<NavigationEngine*>(ptr);
    MetroStation station;
    const char* idStr = env->GetStringUTFChars(id, nullptr);
    station.id = idStr;
    env->ReleaseStringUTFChars(id, idStr);

    const char* nameStr = env->GetStringUTFChars(name, nullptr);
    station.name = nameStr;
    env->ReleaseStringUTFChars(name, nameStr);

    station.latitude = lat;
    station.longitude = lon;

    int lineCount = env->GetArrayLength(lines);
    for (int i = 0; i < lineCount; ++i) {
        jstring line = (jstring)env->GetObjectArrayElement(lines, i);
        const char* lStr = env->GetStringUTFChars(line, nullptr);
        station.lines.push_back(lStr);
        env->ReleaseStringUTFChars(line, lStr);
    }

    engine->getMetroEngine().addStation(station);
}

extern "C" JNIEXPORT void JNICALL
Java_com_khaled_move_navigation_NativeNavigationEngine_addMetroLine(JNIEnv* env, jobject thiz, jlong ptr, jstring id, jstring name, jstring color, jobjectArray stationIds) {
    auto* engine = reinterpret_cast<NavigationEngine*>(ptr);
    MetroLine line;
    const char* idStr = env->GetStringUTFChars(id, nullptr);
    line.id = idStr;
    env->ReleaseStringUTFChars(id, idStr);

    const char* nameStr = env->GetStringUTFChars(name, nullptr);
    line.name = nameStr;
    env->ReleaseStringUTFChars(name, nameStr);

    const char* colorStr = env->GetStringUTFChars(color, nullptr);
    line.colorHex = colorStr;
    env->ReleaseStringUTFChars(color, colorStr);

    int stCount = env->GetArrayLength(stationIds);
    for (int i = 0; i < stCount; ++i) {
        jstring stId = (jstring)env->GetObjectArrayElement(stationIds, i);
        const char* sStr = env->GetStringUTFChars(stId, nullptr);
        line.stationIds.push_back(sStr);
        env->ReleaseStringUTFChars(stId, sStr);
    }

    engine->getMetroEngine().addLine(line);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_khaled_move_navigation_NativeNavigationEngine_findNearbyMetroStation(JNIEnv* env, jobject thiz, jlong ptr, jdouble lat, jdouble lon, jdouble threshold) {
    auto* engine = reinterpret_cast<NavigationEngine*>(ptr);
    auto station = engine->getMetroEngine().findNearbyStation(lat, lon, threshold);
    if (station) {
        return env->NewStringUTF(station->id.c_str());
    }
    return nullptr;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_khaled_move_navigation_NativeNavigationEngine_isAtMetroStation(JNIEnv* env, jobject thiz, jlong ptr, jdouble lat, jdouble lon, jdouble threshold) {
    auto* engine = reinterpret_cast<NavigationEngine*>(ptr);
    return engine->getMetroEngine().isAtStation(lat, lon, threshold);
}
