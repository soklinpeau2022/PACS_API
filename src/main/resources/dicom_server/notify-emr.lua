local function hasText(value)
  return value ~= nil and tostring(value) ~= ''
end

local function readTag(tags, key)
  if tags == nil or key == nil then
    return nil
  end
  local value = tags[key]
  if not hasText(value) then
    return nil
  end
  return tostring(value)
end

local function env(name, fallback)
  local value = os.getenv(name)
  if value == nil or value == '' then
    return fallback
  end
  return value
end

local function shouldNotify()
  local flag = string.lower(env('UDAYA_DICOM_SERVER_CALLBACK_ENABLED', 'true'))
  return flag == 'true' or flag == '1' or flag == 'yes'
end

local function numberEnv(name, fallback)
  local value = tonumber(env(name, ''))
  if value == nil or value <= 0 then
    return fallback
  end
  return value
end

local function tableCount(values)
  if type(values) ~= 'table' then
    return 0
  end
  local count = 0
  for _, _ in pairs(values) do
    count = count + 1
  end
  return count
end

local function numericValue(value)
  if value == nil then
    return nil
  end
  local parsed = tonumber(value)
  if parsed == nil then
    return nil
  end
  return parsed
end

local function studyInstanceCount(study)
  if type(study) ~= 'table' then
    return 0
  end

  local directCount = tableCount(study['Instances'])
  if directCount > 0 then
    return directCount
  end

  local statistics = study['Statistics'] or {}
  local statisticCount = numericValue(statistics['CountInstances'])
    or numericValue(statistics['Instances'])
    or numericValue(statistics['TotalInstances'])
    or numericValue(statistics['InstanceCount'])
  if statisticCount ~= nil and statisticCount > 0 then
    return statisticCount
  end

  return 0
end

local function statisticsInstanceCount(studyId)
  local ok, result = pcall(RestApiGet, '/studies/' .. studyId .. '/statistics')
  if not ok or not hasText(result) then
    return 0
  end

  local statistics = ParseJson(result) or {}
  local statisticCount = numericValue(statistics['CountInstances'])
    or numericValue(statistics['Instances'])
    or numericValue(statistics['TotalInstances'])
    or numericValue(statistics['InstanceCount'])
  if statisticCount ~= nil and statisticCount > 0 then
    return statisticCount
  end

  return 0
end

local function seriesInstanceCount(study)
  if type(study) ~= 'table' or type(study['Series']) ~= 'table' then
    return 0
  end

  local total = 0
  for _, seriesId in pairs(study['Series']) do
    if hasText(seriesId) then
      local ok, result = pcall(RestApiGet, '/series/' .. tostring(seriesId))
      if ok and hasText(result) then
        local series = ParseJson(result) or {}
        total = total + tableCount(series['Instances'])
      end
    end
  end

  return total
end

local function sleepSeconds(seconds)
  local delay = tonumber(seconds) or 0
  if delay <= 0 then
    return
  end
  if Sleep ~= nil then
    Sleep(delay)
  elseif os ~= nil and os.execute ~= nil then
    os.execute('sleep ' .. tostring(delay))
  end
end

local function loadStudyWithInstances(studyId)
  local maxWait = math.floor(numberEnv('UDAYA_DICOM_SERVER_CALLBACK_INSTANCE_WAIT_SECONDS', 30))
  local pollSeconds = math.floor(numberEnv('UDAYA_DICOM_SERVER_CALLBACK_INSTANCE_POLL_SECONDS', 2))
  local waited = 0
  local lastStudy = nil

  while true do
    local ok, result = pcall(RestApiGet, '/studies/' .. studyId)
    if ok and hasText(result) then
      lastStudy = ParseJson(result)
      local instanceCount = studyInstanceCount(lastStudy)
      if instanceCount <= 0 then
        instanceCount = statisticsInstanceCount(studyId)
      end
      if instanceCount <= 0 then
        instanceCount = seriesInstanceCount(lastStudy)
      end
      if instanceCount > 0 then
        return lastStudy, instanceCount
      end
      print('Stable study ' .. studyId .. ' has no instances yet; waited ' .. waited .. '/' .. maxWait .. ' seconds')
    else
      print('Unable to read stable study ' .. studyId .. ': ' .. tostring(result))
    end

    if waited >= maxWait then
      return lastStudy or {}, 0
    end

    sleepSeconds(pollSeconds)
    waited = waited + pollSeconds
  end
end

local function apiResponseSucceeded(result)
  if not hasText(result) then
    return false
  end

  local ok, parsed = pcall(ParseJson, result)
  if not ok or type(parsed) ~= 'table' then
    return true
  end

  local header = parsed['header'] or {}
  if parsed['success'] == false or header['result'] == false then
    return false
  end

  return true
end

local function httpPostWithRetry(label, url, body, headers, requireBody)
  local maxAttempts = math.floor(numberEnv('UDAYA_DICOM_SERVER_CALLBACK_MAX_ATTEMPTS', 3))
  local lastResult = nil

  for attempt = 1, maxAttempts do
    local ok, result = pcall(HttpPost, url, body, headers)
    if ok and (not requireBody or apiResponseSucceeded(result)) then
      return true, result
    end
    lastResult = result
    print(label .. ' failed attempt ' .. attempt .. '/' .. maxAttempts .. ': ' .. tostring(result))
  end

  return false, lastResult
end

local function getCallbackBearerToken()
  local tokenUrl = env('UDAYA_DICOM_SERVER_CALLBACK_TOKEN_URL', '')
  local clientId = env('UDAYA_DICOM_SERVER_CALLBACK_CLIENT_ID', '')
  local clientSecret = env('UDAYA_DICOM_SERVER_CALLBACK_CLIENT_SECRET', '')

  if not hasText(tokenUrl) or not hasText(clientId) or not hasText(clientSecret) then
    print('DicomServer EMR callback token request skipped because callback credentials are incomplete')
    return nil
  end

  local tokenBody = DumpJson({
    clientId = clientId,
    clientSecret = clientSecret,
    scope = 'pacs.api'
  }, true)

  local ok, result = httpPostWithRetry('DicomServer EMR callback token request', tokenUrl, tokenBody, {
    ['Content-Type'] = 'application/json'
  }, true)

  if not ok then
    print('DicomServer EMR callback token request failed: ' .. tostring(result))
    return nil
  end

  local parsed = ParseJson(result)
  local data = parsed['body'] and parsed['body']['data'] or {}
  local first = data[1] or data[0]
  if first == nil then
    return nil
  end

  local accessToken = first['accessToken']
  if not hasText(accessToken) then
    return nil
  end

  return tostring(accessToken)
end

function OnStableStudy(studyId, tags, metadata, origin)
  if not shouldNotify() then
    return
  end

  SetHttpTimeout(numberEnv('UDAYA_DICOM_SERVER_CALLBACK_TIMEOUT_SECONDS', 10))

  local study, instanceCount = loadStudyWithInstances(studyId)
  if instanceCount <= 0 then
    print('Skipping EMR callback for stable study ' .. studyId .. ' because no image instances are available')
    return
  end

  local mainDicomTags = study['MainDicomTags'] or {}
  local patientDicomTags = study['PatientMainDicomTags'] or {}
  local accessionNumber = readTag(mainDicomTags, 'AccessionNumber')

  if not hasText(accessionNumber) then
    print('Skipping EMR callback for stable study ' .. studyId .. ' because AccessionNumber is missing')
    return
  end

  local bearerToken = getCallbackBearerToken()
  if not hasText(bearerToken) then
    print('Skipping EMR callback for stable study ' .. studyId .. ' because machine-client token is unavailable')
    return
  end

  local payload = {
    event = 'STUDY_RECEIVED',
    accessionNumber = accessionNumber,
    dicomServerStudyId = studyId,
    studyInstanceUid = readTag(mainDicomTags, 'StudyInstanceUID'),
    patientId = readTag(patientDicomTags, 'PatientID'),
    patientName = readTag(patientDicomTags, 'PatientName'),
    patientBirthDate = readTag(patientDicomTags, 'PatientBirthDate'),
    patientSex = readTag(patientDicomTags, 'PatientSex'),
    studyDescription = readTag(mainDicomTags, 'StudyDescription'),
    studyDate = readTag(mainDicomTags, 'StudyDate'),
    institutionName = readTag(mainDicomTags, 'InstitutionName'),
    imageInstanceCount = instanceCount
  }

  local headers = {
    ['Content-Type'] = 'application/json',
    ['Authorization'] = 'Bearer ' .. bearerToken
  }
  local body = DumpJson(payload, true)
  local callbackUrl = env('UDAYA_DICOM_SERVER_CALLBACK_URL', '')
  if not hasText(callbackUrl) then
    print('Skipping EMR callback for stable study ' .. studyId .. ' because UDAYA_DICOM_SERVER_CALLBACK_URL is not configured')
    return
  end

  local ok, result = httpPostWithRetry('DicomServer EMR callback for stable study ' .. studyId, callbackUrl, body, headers, true)
  if not ok then
    print('DicomServer EMR callback failed for stable study ' .. studyId .. ': ' .. tostring(result))
    return
  end

  print('DicomServer EMR callback sent for stable study ' .. studyId .. ' accession=' .. accessionNumber)
end
