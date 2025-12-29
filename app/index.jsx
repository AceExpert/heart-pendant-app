import { Link } from 'expo-router';
import { StyleSheet, View, Text } from 'react-native';

import { StatusBar } from "expo-status-bar";

import { SafeAreaProvider, SafeAreaView } from 'react-native-safe-area-context';

import { styles as globalstyles } from "../styles/global";

import NativeBluetooth from "../specs/NativeBluetooth";

export default function HomeScreen() {
  return (
    <SafeAreaProvider>
      <SafeAreaView style={[globalstyles.column, globalstyles.center, {width: "100%"}]}>
        <StatusBar style='dark' animated={true} hideTransitionAnimation='slide'/>
        
        <View style={[globalstyles.column, globalstyles.center, {width: "100%", paddingHorizontal: 15, marginTop: 10}]}>
          <Text style={{fontSize: 18, fontWeight: "500"}}>Dil Ki Dor</Text>
          <Text style={{fontWeight: "400", color: "grey", fontSize: 13}}>by Anshul for Mumma & <Text style={{color: "maroon"}}>Shaleen</Text></Text>
        </View>

        <View style={[globalstyles.column, {width: "100%", paddingHorizontal: 15, marginTop: 20}]}>
          <View style={[globalstyles.column, {width: "100%", height: 300, backgroundColor: "rgba(255, 238, 230, 1)", borderRadius: 20, paddingVertical: 15, paddingHorizontal: 20}]}>
            <Text style={{fontSize: 20, fontWeight: "600"}}>Your Pendant</Text>
            <Text style={{fontSize: 12, fontWeight: "400", color: "grey"}}>Mumma's Heart</Text>
          </View>
        </View>


        <View style={[globalstyles.column, {width: "100%", paddingHorizontal: 15, marginTop: 30}]}>
          <View style={[globalstyles.column, {width: "100%", height: 300, backgroundColor: "whitesmoke", borderRadius: 20, paddingVertical: 15, paddingHorizontal: 20}]}>
            <Text style={{fontSize: 20, fontWeight: "600"}}><Text style={{color: "maroon"}}>Shaleen</Text>'s Heart</Text>
            <Text style={{fontSize: 12, fontWeight: "400", color: "grey"}}>Pendant with your mumma</Text>
          </View>
        </View>

      </SafeAreaView>
    </SafeAreaProvider>
  );
}

const styles = StyleSheet.create({
  
});
