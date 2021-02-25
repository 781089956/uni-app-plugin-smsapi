<template>
	<view class="content">
		来源：<input v-model="source" type="text" />
		号码：<input v-model="number" type="text" />
		内容：<input v-model="content" type="text" />
		时间戳：<input v-model="time" type="text" />
		会话ID：<input v-model="conversationID" type="text" />
		SIM_ID：<input v-model="SIMCardID" type="text" />


		<button class="btn" @click='add()'>添加短信测试</button>
		<button class="btn" @click='goDefaultApp()'>前往默认应用设置（备用）</button>
		<button class="btn" @click='isDefaultApp()'>检测是否默认App</button>
		<button class="btn" @click='setDefaultApp()'>设置当前应用为默认短信</button>
		<button class="btn" @click='restoreDefaultApp()'>恢复默认短信应用</button>
		<button class="btn" @click='checkPermission()'>检查短信权限</button>
		<button class="btn" @click='requestPermission()'>获取短信权限</button>
		<button class="btn" @click='readSmsMsg()'>读取短信（测试用）</button>
		<div class="pstate" :style="{backgroundColor: hasPermission ? 'green': 'red'}">权限状态</div>
		<!-- <button class="btn" @click='delAllMessage()'>删除测试</button> -->

	</view>
</template>

<script>
	const smsapi = uni.requireNativePlugin('SMS-API');
	const modal = uni.requireNativePlugin('modal');
	export default {
		data() {
			return {
				source: '',
				number: '',
				content: '',
				time: '',
				conversationID: '',
				SIMCardID: '',
				hasPermission: false,
			}
		},
		onLoad() {

		},
		methods: {
			goDefaultApp() {
				smsapi.goDefaultApp();
			},
			setDefaultApp() {
				smsapi.setDefaultApp();
				
			},
			restoreDefaultApp() {
				smsapi.restoreDefaultApp();
			},

			checkPermission() {
				console.log("开始检查权限");
				let res = smsapi.checkPermission();
				if (res.code === "fail") {
					console.log(' 权限未获取');
					this.hasPermission = false;
				} else {
					console.log('权限已获取');
					this.hasPermission = true;
				}
				modal.toast({
					message: res,
					duration: 1.5
				})
			},
			requestPermission() {
				console.log("开始请求权限");
				smsapi.requestPermission(data => {
					if (data.code === 'success') {
						console.log('权限请求成功');
						this.hasPermission = true;
					} else {
						console.log('权限请求失败');
						this.hasPermission = false;
					}
					modal.toast({
						message: data,
						duration: 1.5
					})
				});
			},
			test() {
				smsapi.test();
			},
			add() {

				let obj = {
					source: this.source,
					number: this.number,
					content: this.content,
					time: this.time,
					conversationID: this.conversationID,
					SIMCardID: this.SIMCardID
				}
				let ret = smsapi.addSmsMsg(obj);
				
				modal.toast({
					message: ret,
					duration: 1.5
				})
				
			},
			readSmsMsg() {
				smsapi.readSmsMsg();
			},
			isDefaultApp() {
				modal.toast({
					message: smsapi.isDefaultApp(),
					duration: 1.5
				})
			},
			

		}
	}
</script>

<style>
	.pstate {
		display: flex;
		flex-direction: column;
		justify-content: center;
		align-items: center;
		color: #555555;
		width: 100%;
	}
</style>
